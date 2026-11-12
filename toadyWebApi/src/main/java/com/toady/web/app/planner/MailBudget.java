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
    private final JdbcTemplate jdbc; private final int limit; private final int reserve;
    public MailBudget(JdbcTemplate jdbc,@Value("${toady.planner.mail-limit:400}") int limit,@Value("${toady.planner.auth-reserve:100}") int reserve) {
        if(limit<1 || limit>400 || reserve<0 || reserve>=limit) throw new IllegalArgumentException("메일 제한은 1~400, 인증 예약량은 제한보다 작아야 합니다.");
        this.jdbc=jdbc;this.limit=limit;this.reserve=reserve;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean reserve(boolean reminder) {
        jdbc.queryForObject("SELECT ID FROM TB_MAIL_BUDGET WHERE ID=1 FOR UPDATE",Integer.class);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        long used=jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=?",Long.class,Timestamp.valueOf(now.minusHours(24)));
        long reminders=reminder ? jdbc.queryForObject("SELECT COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=? AND MAIL_KIND='REMINDER'",Long.class,Timestamp.valueOf(now.minusHours(24))) : 0;
        if(used>=limit || (reminder && reminders>=limit-reserve)) return false;
        jdbc.update("INSERT INTO TB_MAIL_ATTEMPT (ID,MAIL_KIND,REG_DT) VALUES (?,?,?)",UUID.randomUUID().toString(),reminder?"REMINDER":"AUTH",Timestamp.valueOf(now));
        // 전송 시도 이력에는 주소·본문을 저장하지 않으며 7일 경과분만 소량 정리한다.
        jdbc.update("DELETE FROM TB_MAIL_ATTEMPT WHERE REG_DT<?",Timestamp.valueOf(now.minusDays(7)));
        return true;
    }
}
