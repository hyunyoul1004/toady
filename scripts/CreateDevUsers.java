import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 개발 DB에 실제 회원처럼 보이는 테스트 계정을 만든다.
 * - 닉네임: 동네·청약 상황·생활을 섞은 자연스러운 이름(가입 규칙: 한글·영문·숫자 2~15자, 서로 다름).
 * - 이메일: 실제처럼 보이되 메일이 가지 않는 예약 최상위 도메인(.test)만 쓴다(naver.test·gmail.test 등).
 *   진짜 도메인을 쓰면 비밀번호 찾기·알림 메일이 실제 사람에게 가거나 Gmail 하루 발송량을 쓴다.
 * - 가입일: 최근 반년에 고르게 퍼지게(최근일수록 조금 더 많게), 모두 이틀 이상 지난 날짜.
 * - 프로필 캐릭터: 열에 일곱 정도만 고른 상태(나머지는 닉네임 첫 글자).
 * - 같은 번호(1~N)는 항상 같은 회원으로 만들어지므로 여러 번 실행해도 결과가 같다.
 *   예전 형식(devNNNN@example.test)으로 만든 계정이 있으면 회원 번호는 그대로 두고 이 형식으로 바꾼다.
 * - 비밀번호는 표준 입력으로 받아 BCrypt 해시만 저장한다(파일·로그에 남기지 않음).
 * 사용: scripts/create-dev-users.sh [개수, 기본 1000]  /  목록만: ... CreateDevUsers.java <개수> <env> --list
 */
public class CreateDevUsers {
    static final String[] AREAS = {"운정", "동탄", "송도", "마곡", "판교", "위례", "광교", "청라", "검단", "다산", "별내", "미사", "고덕", "평택",
            "세종", "하남", "김포", "부천", "일산", "분당", "용인", "수원", "인천", "구리", "남양주", "시흥", "안산", "의왕", "과천", "왕십리"};
    static final String[] ROLES = {"맘", "아빠", "댁", "신혼", "직장인", "새댁", "러", "주민", "토박이", "엄마", "부부", "살이"};
    static final String[] SITUATIONS = {"청약초보", "청약준비중", "무주택", "내집마련", "특공도전", "신특준비", "생애최초", "줍줍헌터", "청약공부중",
            "당첨기원", "청약러", "분양관심", "가점올리기", "전세탈출", "이사준비", "신혼집찾기", "첫집장만", "청약일기"};
    static final String[] NICK_WORDS = {"하늘", "바다", "구름", "햇살", "봄날", "솔이", "다온", "하루", "별빛", "도토리", "콩이", "보리", "초코",
            "모카", "라떼", "토리", "두부", "감자", "호두", "여름", "가을", "겨울", "새벽", "노을", "민트", "레몬", "복숭아", "단비"};
    static final String[] FAMILY = {"네", "맘", "아빠", "엄마", "네집", "언니", "누나", "형"};
    static final String[] EN_WORDS = {"home", "sunny", "happy", "dream", "blue", "cozy", "lucky", "daily", "mint", "nest", "maple", "soda", "moon", "pine"};
    static final String[] GIVEN = {"minji", "jiwoo", "seoyeon", "hyunwoo", "jihoon", "yuna", "sumin", "dohyun", "eunji", "junho", "hayoung", "seojun",
            "jiyeon", "minseok", "sohee", "taeyang", "yejin", "donghyun", "chaeyoung", "kyungmin", "soyoung", "jaewon", "hyejin", "sungmin"};
    static final String[] SURNAME = {"kim", "lee", "park", "choi", "jung", "kang", "cho", "yoon", "jang", "lim", "han", "oh", "seo", "shin", "kwon"};
    static final String[] DOMAINS = {"naver.test", "naver.test", "naver.test", "naver.test", "gmail.test", "gmail.test", "gmail.test",
            "daum.test", "hanmail.test", "kakao.test"};
    static final String[] ICONS = {"toad", "house", "cat", "dog", "rabbit", "bear", "panda", "penguin", "chick", "fox", "hamster", "pig"};

    record Member(String email, String nickname, String icon, LocalDateTime joinedAt) { }

    static String pick(Random r, String[] values) { return values[r.nextInt(values.length)]; }

    static String nickname(Random r) {
        return switch (r.nextInt(8)) {
            case 0, 1 -> pick(r, AREAS) + pick(r, ROLES);                                  // 운정맘, 동탄직장인
            case 2 -> pick(r, SITUATIONS);                                                  // 청약초보
            case 3 -> pick(r, SITUATIONS) + (2024 + r.nextInt(3));                          // 내집마련2026
            case 4 -> pick(r, NICK_WORDS) + pick(r, FAMILY);                                // 하늘이네 대신 하늘네
            case 5 -> pick(r, AREAS) + pick(r, SITUATIONS).replace("청약", "");             // 위례무주택
            case 6 -> "가점" + (30 + r.nextInt(45)) + "점";                                  // 가점64점
            default -> pick(r, EN_WORDS) + pick(r, NICK_WORDS);                             // sunny하늘
        };
    }

    static String emailLocal(Random r) {
        String given = pick(r, GIVEN), surname = pick(r, SURNAME);
        int year = 75 + r.nextInt(24);                       // 1975~1998년생
        return switch (r.nextInt(6)) {
            case 0 -> given + "." + surname + String.format("%02d", year % 100);
            case 1 -> surname.charAt(0) + given + String.format("%02d%02d", 1 + r.nextInt(12), 1 + r.nextInt(28));
            case 2 -> { String first = pick(r, EN_WORDS), second = pick(r, EN_WORDS); while (second.equals(first)) second = pick(r, EN_WORDS); yield first + second + (10 + r.nextInt(90)); }
            case 3 -> given + surname + (r.nextBoolean() ? String.valueOf(1900 + year) : "");
            case 4 -> given.charAt(0) + "" + given.charAt(given.length() - 1) + surname + (100 + r.nextInt(900));
            default -> surname + "_" + given;
        };
    }

    /** 1~count번 회원을 만든다. 같은 count면 항상 같은 결과(번호별 고정 시드). */
    static List<Member> members(int count, LocalDateTime now) {
        Set<String> emails = new HashSet<>(), nicks = new HashSet<>();
        List<Member> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Random r = new Random(20261116L * 31 + i);
            String nick = nickname(r);
            // 이미 쓰인 닉네임이면 실제 서비스처럼 숫자를 붙인다(15자 넘으면 다시 뽑는다).
            for (int tries = 0; nicks.contains(nick) || nick.length() > 15 || nick.length() < 2; tries++) {
                String base = nickname(r);
                // 몇 번 다시 뽑아도 겹치면 숫자 두 자리를 붙인다(이미 숫자로 끝나는 이름에는 붙이지 않는다).
                if (tries >= 3 && !Character.isDigit(base.charAt(base.length() - 1))) base = base + (r.nextInt(90) + 10);
                nick = base;
            }
            nicks.add(nick);
            String email = emailLocal(r) + "@" + pick(r, DOMAINS);
            while (emails.contains(email)) email = emailLocal(r) + (r.nextInt(90) + 10) + "@" + pick(r, DOMAINS);
            emails.add(email);
            String icon = r.nextInt(10) < 7 ? pick(r, ICONS) : null;
            // 최근일수록 가입이 조금 더 많게(제곱 분포), 2~180일 전
            double t = r.nextDouble();
            long minutesAgo = (long) ((2 + 178 * t * t) * 24 * 60) + r.nextInt(24 * 60);
            list.add(new Member(email, nick, icon, now.minusMinutes(minutesAgo)));
        }
        return list;
    }

    public static void main(String[] args) throws Exception {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        Path envFile = Path.of(args.length > 1 ? args[1] : "toadyWebApi/.env");
        LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).withSecond(0).withNano(0);
        List<Member> members = members(count, now);
        if (args.length > 2 && args[2].equals("--list")) {
            System.out.println("email,nickname");
            members.forEach(m -> System.out.println(m.email() + "," + m.nickname()));
            return;
        }
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
            c.setAutoCommit(false);
            int added = 0, converted = 0, kept = 0, skipped = 0;
            try (PreparedStatement findLegacy = c.prepareStatement("SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL=?");
                 PreparedStatement takenNick = c.prepareStatement("SELECT USER_SEQ FROM TB_USER WHERE USER_NIC_NAME=?");
                 PreparedStatement convert = c.prepareStatement("UPDATE TB_USER SET USER_EMAIL=?, USER_NIC_NAME=?, USER_PASSWORD=?, CREATE_DT=?, UPDATE_DT=? WHERE USER_SEQ=?");
                 PreparedStatement insert = c.prepareStatement("INSERT INTO TB_USER (USER_EMAIL,USER_PASSWORD,USER_NIC_NAME,EMAIL_CERTIFY_YN,LOCK_YN,WITHDRAWAL_YN,CREATE_DT,UPDATE_DT)"
                         + " VALUES (?,?,?,'Y','N','N',?,?)", Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < members.size(); i++) {
                    Member m = members.get(i);
                    Timestamp joined = Timestamp.valueOf(m.joinedAt());
                    Long existing = seq(findLegacy, m.email());
                    if (existing != null) { kept++; setIcon(c, existing, m.icon(), joined); continue; }
                    Long owner = seq(takenNick, m.nickname());
                    Long legacy = seq(findLegacy, String.format("dev%04d@example.test", i + 1));
                    if (owner != null && !owner.equals(legacy)) { skipped++; continue; }   // 실제 회원이 쓰는 닉네임이면 건너뛴다
                    long userSeq;
                    if (legacy != null) {
                        convert.setString(1, m.email()); convert.setString(2, m.nickname()); convert.setString(3, hash);
                        convert.setTimestamp(4, joined); convert.setTimestamp(5, joined); convert.setLong(6, legacy);
                        convert.executeUpdate(); userSeq = legacy; converted++;
                    } else {
                        insert.setString(1, m.email()); insert.setString(2, hash); insert.setString(3, m.nickname());
                        insert.setTimestamp(4, joined); insert.setTimestamp(5, joined); insert.executeUpdate();
                        try (ResultSet k = insert.getGeneratedKeys()) { k.next(); userSeq = k.getLong(1); }
                        added++;
                    }
                    setIcon(c, userSeq, m.icon(), joined);
                }
            }
            c.commit();
            System.out.println("새로 만듦 " + added + "개, 예전 형식에서 바꿈 " + converted + "개, 이미 있음 " + kept + "개, 닉네임이 겹쳐 건너뜀 " + skipped + "개");
            try (ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*) FROM TB_USER WHERE USER_EMAIL LIKE '%.test' AND USER_EMAIL NOT LIKE '%@example.test'")) {
                r.next(); System.out.println("개발용 계정 합계 " + r.getInt(1) + "개");
            }
        }
    }

    static Long seq(PreparedStatement statement, String value) throws SQLException {
        statement.setString(1, value);
        try (ResultSet r = statement.executeQuery()) { return r.next() ? r.getLong(1) : null; }
    }

    /** 프로필 캐릭터(TB_USER_PROFILE, database.sql 23번). 테이블이 없으면 건너뛴다. */
    static void setIcon(Connection c, long userSeq, String icon, Timestamp at) {
        try (Statement savepoint = c.createStatement()) {
            savepoint.execute("SAVEPOINT icon");
            try (PreparedStatement delete = c.prepareStatement("DELETE FROM TB_USER_PROFILE WHERE USER_SEQ=?")) { delete.setLong(1, userSeq); delete.executeUpdate(); }
            if (icon != null) try (PreparedStatement insert = c.prepareStatement("INSERT INTO TB_USER_PROFILE (USER_SEQ, PROFILE_ICON, UPDATE_DT) VALUES (?,?,?)")) {
                insert.setLong(1, userSeq); insert.setString(2, icon); insert.setTimestamp(3, at); insert.executeUpdate();
            }
            savepoint.execute("RELEASE SAVEPOINT icon");
        } catch (SQLException missing) {
            try (Statement rollback = c.createStatement()) { rollback.execute("ROLLBACK TO SAVEPOINT icon"); } catch (SQLException ignore) { }
        }
    }
}
