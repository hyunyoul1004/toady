package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Gmail 계정 하나의 하루 발송량(약 500통)을 나눠 쓰는 예산 관리자.
 * 발송 시도마다 TB_MAIL_ATTEMPT에 한 줄 남기고, TB_MAIL_BUDGET 한 행을 잠가(FOR UPDATE) 동시에 여러 서버·스레드가 세도 겹치지 않게 한다.
 * 실패도 용량을 차감해 보수적으로 운영한다. 메일을 보내기 전 EmailUtilService가 reserve()를 부른다.
 */
// @Service: 스프링 빈으로 등록한다.
@Service
public class MailBudget {
    /** AUTH: 비밀번호 찾기 등 인증 메일, SIGNUP: 회원가입 인증 메일, REMINDER: 일정 알림. MAIL_KIND(VARCHAR 10)에 그대로 저장한다. */
    public enum Kind { AUTH, SIGNUP, REMINDER }
    private final JdbcTemplate jdbc; private final int limit; private final int reserve; private final int signupLimit;
    // @Value("${키:기본값}"): application 설정(환경변수)에서 값을 읽어 넣는다. 없으면 콜론 뒤 기본값을 쓴다.
    public MailBudget(JdbcTemplate jdbc,@Value("${toady.planner.mail-limit:400}") int limit,@Value("${toady.planner.auth-reserve:100}") int reserve,
            @Value("${toady.planner.signup-limit:0}") int signupLimit) {
        if(limit<1 || limit>400 || reserve<0 || reserve>=limit) throw new IllegalArgumentException("메일 제한은 1~400, 인증 예약량은 제한보다 작아야 합니다.");
        // 0이면 전체 제한의 절반(기본 400 → 200)을 가입 메일 상한으로 쓴다.
        if(signupLimit==0) signupLimit=Math.max(1,limit/2);
        if(signupLimit<1 || signupLimit>=limit) throw new IllegalArgumentException("가입 메일 상한은 전체 제한보다 작아야 합니다.");
        this.jdbc=jdbc;this.limit=limit;this.reserve=reserve;this.signupLimit=signupLimit;
    }
    /** 관리자 화면 서비스 설정(database.sql 27번). 값이 없거나 규칙(1~400, 예약량·가입 상한 < 전체)에 맞지 않으면 환경변수 값을 쓴다. */
    // @Autowired(required=false): AppSettings 빈이 있으면 필드에 넣고, 없으면(테스트 등) null로 둔다.
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.toady.web.app.operation.AppSettings settings;
    int limit() { return settings==null?limit:(int)settings.number(com.toady.web.app.operation.AppSettings.MAIL_LIMIT,limit,1,400); }
    int reserve(int effectiveLimit) {
        long value=settings==null?reserve:settings.number(com.toady.web.app.operation.AppSettings.MAIL_AUTH_RESERVE,reserve,0,399);
        return (int)Math.min(value,effectiveLimit-1);
    }
    int signupLimit(int effectiveLimit) {
        long value=settings==null?signupLimit:settings.number(com.toady.web.app.operation.AppSettings.MAIL_SIGNUP_LIMIT,signupLimit,0,399);
        if(value==0) value=Math.max(1,effectiveLimit/2);
        return (int)Math.max(1,Math.min(value,effectiveLimit-1));
    }
    /** 이전 호출부 호환: true면 일정 알림, false면 인증 메일. 내부 호출은 프록시를 거치지 않으므로 여기에도 트랜잭션을 건다(잠금 유지). */
    // @Transactional(REQUIRES_NEW): 부른 쪽 트랜잭션과 상관없이 새 트랜잭션을 연다.
    // 그래서 예산 기록이 바로 커밋되고, 바깥 작업이 실패해도 차감 기록은 남는다.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean reserve(boolean reminder) { return reserve(reminder?Kind.REMINDER:Kind.AUTH); }
    /**
     * 최근 24시간 발송 시도를 DB 잠금 아래 센다. 인증 메일이 아닌 메일(일정 알림·가입 메일)은 합쳐서 인증 예약량을 남기고,
     * 회원가입 메일은 별도 상한(기본 전체의 절반)도 넘지 않아 무작위 주소로 가입을 반복해도 비밀번호 찾기 메일 몫이 남는다.
     */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean reserve(Kind kind) {
        jdbc.queryForObject("SELECT ID FROM TB_MAIL_BUDGET WHERE ID=1 FOR UPDATE",Integer.class);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        Timestamp since=Timestamp.valueOf(now.minusHours(24));
        long used=jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=?",Long.class,since);
        int limit=limit(), reserve=reserve(limit), signupLimit=signupLimit(limit);
        if(used>=limit) return false;
        // 인증 메일(비밀번호 찾기 등)이 아닌 메일은 종류를 합쳐서도 인증용 예약분을 건드리지 못한다.
        // (일정 알림과 가입 메일이 각자 상한 안이어도 합치면 예약분까지 써 버리는 경우를 막는다)
        if(kind!=Kind.AUTH && used>=limit-reserve) return false;
        if(kind==Kind.REMINDER && jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=? AND MAIL_KIND='REMINDER'",Long.class,since)>=limit-reserve) return false;
        if(kind==Kind.SIGNUP && jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=? AND MAIL_KIND='SIGNUP'",Long.class,since)>=signupLimit) return false;
        jdbc.update("INSERT INTO TB_MAIL_ATTEMPT (ID,MAIL_KIND,REG_DT) VALUES (?,?,?)",UUID.randomUUID().toString(),kind.name(),Timestamp.valueOf(now));
        // 전송 시도 이력에는 주소·본문을 저장하지 않으며 7일 경과분만 소량 정리한다.
        jdbc.update("DELETE FROM TB_MAIL_ATTEMPT WHERE REG_DT<?",Timestamp.valueOf(now.minusDays(7)));
        return true;
    }
}
