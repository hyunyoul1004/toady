package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;

/** Gmail 계정 하나의 발송 시도를 DB로 직렬화한다. 실패도 용량을 차감해 보수적으로 운영한다. */
@Service
public class MailBudget {
    /** AUTH: 비밀번호 찾기 등 인증 메일, SIGNUP: 회원가입 인증 메일, REMINDER: 일정 알림. MAIL_KIND(VARCHAR 10)에 그대로 저장한다. */
    public enum Kind { AUTH, SIGNUP, REMINDER }
    private final JdbcTemplate jdbc; private final int limit; private final int reserve; private final int signupLimit;
    public MailBudget(JdbcTemplate jdbc,@Value("${toady.planner.mail-limit:400}") int limit,@Value("${toady.planner.auth-reserve:100}") int reserve,
            @Value("${toady.planner.signup-limit:0}") int signupLimit) {
        if(limit<1 || limit>400 || reserve<0 || reserve>=limit) throw new IllegalArgumentException("메일 제한은 1~400, 인증 예약량은 제한보다 작아야 합니다.");
        // 0이면 전체 제한의 절반(기본 400 → 200)을 가입 메일 상한으로 쓴다.
        if(signupLimit==0) signupLimit=Math.max(1,limit/2);
        if(signupLimit<1 || signupLimit>=limit) throw new IllegalArgumentException("가입 메일 상한은 전체 제한보다 작아야 합니다.");
        this.jdbc=jdbc;this.limit=limit;this.reserve=reserve;this.signupLimit=signupLimit;
    }
    /** 이전 호출부 호환: true면 일정 알림, false면 인증 메일. 내부 호출은 프록시를 거치지 않으므로 여기에도 트랜잭션을 건다(잠금 유지). */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean reserve(boolean reminder) { return reserve(reminder?Kind.REMINDER:Kind.AUTH); }
    /**
     * 최근 24시간 발송 시도를 DB 잠금 아래 센다. 일정 알림은 인증 예약량을 남기고, 회원가입 메일은 별도 상한(기본 전체의 절반)을 넘지 않아
     * 무작위 주소로 가입을 반복해도 비밀번호 찾기 메일 몫이 남는다.
     */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean reserve(Kind kind) {
        jdbc.queryForObject("SELECT ID FROM TB_MAIL_BUDGET WHERE ID=1 FOR UPDATE",Integer.class);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        Timestamp since=Timestamp.valueOf(now.minusHours(24));
        long used=jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=?",Long.class,since);
        if(used>=limit) return false;
        if(kind==Kind.REMINDER && jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=? AND MAIL_KIND='REMINDER'",Long.class,since)>=limit-reserve) return false;
        if(kind==Kind.SIGNUP && jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=? AND MAIL_KIND='SIGNUP'",Long.class,since)>=signupLimit) return false;
        jdbc.update("INSERT INTO TB_MAIL_ATTEMPT (ID,MAIL_KIND,REG_DT) VALUES (?,?,?)",UUID.randomUUID().toString(),kind.name(),Timestamp.valueOf(now));
        // 전송 시도 이력에는 주소·본문을 저장하지 않으며 7일 경과분만 소량 정리한다.
        jdbc.update("DELETE FROM TB_MAIL_ATTEMPT WHERE REG_DT<?",Timestamp.valueOf(now.minusDays(7)));
        return true;
    }
}
