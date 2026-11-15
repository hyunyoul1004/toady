import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * 개발 DB에 테스트 계정을 만든다: devNNNN@example.test / 개발회원NNNN.
 * - example.test는 메일이 가지 않는 예약 도메인이다.
 * - 이메일 인증 완료 상태, 가입일은 7일 전(쪽지의 '가입 하루' 조건을 바로 시험할 수 있게).
 * - 이미 있는 계정은 건너뛰므로 여러 번 실행해도 된다.
 * - 비밀번호는 표준 입력으로 받아 BCrypt 해시만 저장한다(파일·로그에 남기지 않음).
 * 사용: scripts/create-dev-users.sh [개수, 기본 1000]
 */
public class CreateDevUsers {
    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        Path envFile = Path.of(args.length > 1 ? args[1] : "toadyWebApi/.env");
        Map<String, String> env = new HashMap<>();
        for (String line : Files.readAllLines(envFile)) {
            int i = line.indexOf('=');
            if (line.startsWith("#") || i < 1) continue;
            String value = line.substring(i + 1).trim();
            if (value.length() > 1 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) value = value.substring(1, value.length() - 1);
            env.put(line.substring(0, i).trim(), value);
        }
        String password = new Scanner(System.in).nextLine();
        if (password.isBlank()) throw new IllegalArgumentException("비밀번호가 비어 있습니다.");
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password);

        try (Connection c = DriverManager.getConnection(env.get("DB_URL"), env.get("DB_USERNAME"), env.get("DB_PASSWORD"))) {
            Set<String> existing = new HashSet<>();
            try (ResultSet r = c.createStatement().executeQuery("SELECT USER_EMAIL FROM TB_USER WHERE USER_EMAIL LIKE 'dev%@example.test'")) {
                while (r.next()) existing.add(r.getString(1));
            }
            c.setAutoCommit(false);
            int added = 0;
            try (PreparedStatement p = c.prepareStatement("INSERT INTO TB_USER (USER_EMAIL,USER_PASSWORD,USER_NIC_NAME,EMAIL_CERTIFY_YN,LOCK_YN,WITHDRAWAL_YN,CREATE_DT)"
                    + " VALUES (?,?,?,'Y','N','N',DATE_SUB(NOW(), INTERVAL 7 DAY))")) {
                for (int i = 1; i <= count; i++) {
                    String n = String.format("%04d", i);
                    String email = "dev" + n + "@example.test";
                    if (existing.contains(email)) continue;
                    p.setString(1, email); p.setString(2, hash); p.setString(3, "개발회원" + n);
                    p.addBatch();
                    if (++added % 200 == 0) p.executeBatch();
                }
                p.executeBatch();
            }
            c.commit();
            try (ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*), MIN(USER_SEQ), MAX(USER_SEQ) FROM TB_USER WHERE USER_EMAIL LIKE 'dev%@example.test'")) {
                r.next();
                System.out.println("추가 " + added + "개, 전체 테스트 계정 " + r.getInt(1) + "개 (회원 번호 " + r.getLong(2) + "~" + r.getLong(3) + ")");
            }
        }
    }
}
