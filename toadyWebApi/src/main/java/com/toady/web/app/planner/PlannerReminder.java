package com.toady.web.app.planner;

import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.util.email.EmailUtilService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 매일 한 회원에게 오늘/내일의 관심 일정을 묶어 한 통만 보낸다. 실제 메일 발송은 명시적 활성화 후 시작한다. */
@Service
@ConditionalOnProperty(name="toady.planner.reminders-enabled",havingValue="true")
public class PlannerReminder {
    @Configuration @EnableScheduling static class Scheduling { }
    private final JdbcTemplate jdbc; private final PlannerService planner; private final UserRepository users;
    private final EmailUtilService mail; private final String domain;
    /** 회원별로 '오늘 보낼 일정 없음'을 확인한 관심 공고 상태. 날짜·관심 공고가 바뀌면 다시 확인한다. */
    private final Map<Long,String> nothingToSend=new ConcurrentHashMap<>();
    private volatile LocalDate memoDate;
    public PlannerReminder(JdbcTemplate jdbc,PlannerService planner,UserRepository users,EmailUtilService mail,
            @Value("${toady.web.domain}") String domain) { this.jdbc=jdbc;this.planner=planner;this.users=users;this.mail=mail;this.domain=domain.replaceAll("/+$",""); }
    /** 정시에 서버가 중단되어도 08~18시 사이 재기동 후 실행할 수 있도록 30분마다 확인한다. */
    @Scheduled(cron="0 0,30 8-18 * * *",zone="Asia/Seoul")
    public void send() {
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul"));
        // 날짜가 바뀌면 전날 메모를 비워 회원 수만큼 쌓인 항목이 남지 않게 한다.
        if(!today.equals(memoDate)) { nothingToSend.clear(); memoDate=today; }
        // SMTP 결과가 불명확한 작업은 자동 재발송하지 않아 중복을 줄인다.
        jdbc.update("UPDATE TB_REMINDER_DELIVERY SET STATUS='UNKNOWN' WHERE STATUS='SENDING' AND UPDATED_AT<?",Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(15)));
        List<Long> recipients=jdbc.queryForList("SELECT DISTINCT USER_SEQ FROM TB_FAVORITE WHERE EMAIL_ENABLED=TRUE ORDER BY USER_SEQ",Long.class);
        for(long id:recipients) {
            try { sendTo(id,today); }
            catch(Exception e) { LoggerFactory.getLogger(getClass()).warn("관심 일정 알림 처리 실패: userSeq={}, error={}",id,e.getClass().getSimpleName()); }
        }
    }
    void sendTo(long id,LocalDate today) throws Exception {
        var account=users.findById(id).orElse(null);
        if(account==null || !"Y".equals(account.getEmailCertifyYn()) || !"N".equals(account.getWithdrawalYn()) || !"N".equals(account.getLockYn())) return;
        // 이미 전송을 시도한 날짜에는 공고를 다시 읽거나 중복 발송하지 않는다.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM TB_REMINDER_DELIVERY WHERE USER_SEQ=? AND SEND_DATE=?",Integer.class,id,Date.valueOf(today))>0) return;
        // 오늘 이미 확인했는데 보낼 일정이 없었고 관심 공고도 그대로면 30분마다 공고를 다시 읽지 않는다.
        String signature=jdbc.queryForObject("SELECT CONCAT(COUNT(*),'/',COALESCE(MAX(REG_DT),'')) FROM TB_FAVORITE WHERE USER_SEQ=? AND EMAIL_ENABLED=TRUE",String.class,id);
        // 같은 날이라도 3시간마다 다시 확인한다. 수집기가 공고 일정을 고쳐 내일 일정이 새로 생긴 경우를 놓치지 않기 위해서다.
        String checked=today+"|"+(LocalDateTime.now(ZoneId.of("Asia/Seoul")).getHour()/3)+"|"+signature;
        if(checked.equals(nothingToSend.get(id))) return;
        StringBuilder lines=new StringBuilder();
        for(var favorite:planner.list(id)) {
            if(!favorite.emailEnabled() || favorite.summary().unavailable()) continue;
            for(var event:favorite.summary().events()) {
                if((event.date().equals(today) || event.date().equals(today.plusDays(1))) && lines.length()<20000) {
                    String link=domain+"/announcements/"+favorite.id();
                    lines.append("<li style='margin:16px 0'><a href='"+HtmlUtils.htmlEscape(link)+"'>")
                        .append(HtmlUtils.htmlEscape(favorite.summary().houseNm())).append("</a><br>")
                        .append(event.date()).append(" · ").append(HtmlUtils.htmlEscape(event.label())).append("</li>");
                }
            }
        }
        if(lines.isEmpty()) { nothingToSend.put(id,checked); return; }
        try { jdbc.update("INSERT INTO TB_REMINDER_DELIVERY (USER_SEQ,SEND_DATE,STATUS,UPDATED_AT) VALUES (?,?,'SENDING',?)",id,Date.valueOf(today),Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC))); }
        catch(DuplicateKeyException duplicate) { return; }
        String body="<!doctype html><html lang='ko'><meta charset='UTF-8'><body style='margin:0;background:#f3f6f9;font-family:Arial,sans-serif;color:#172b3a'><table role='presentation' width='100%'><tr><td align='center' style='padding:32px 16px'><table role='presentation' width='100%' style='max-width:560px;background:white;border-radius:16px'><tr><td style='padding:32px'><b style='color:#087f73'>TOADY</b><h1 style='font-size:24px'>오늘과 내일, 나의 청약 일정</h1><p>저장한 관심 공고의 확인할 일정을 모았습니다.</p><ul style='padding-left:20px'>"+lines+"</ul><p style='font-size:13px;color:#64748b'>공개된 공고 데이터 기준이며 신청 자격과 최종 일정은 공식 모집공고에서 확인해 주세요.</p><hr style='border:0;border-top:1px solid #e2e8f0'><p><a href='"+HtmlUtils.htmlEscape(domain+"/my")+"'>나의 청약 · 알림 설정 및 전체 해제</a></p></td></tr></table></td></tr></table></body></html>";
        try {
            mail.sendReminder(account.getUserEmail(),"[TOADY] 오늘과 내일의 관심 청약 일정",body);
            jdbc.update("UPDATE TB_REMINDER_DELIVERY SET STATUS='SENT',UPDATED_AT=? WHERE USER_SEQ=? AND SEND_DATE=?",Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)),id,Date.valueOf(today));
        } catch(MailQuotaException quota) {
            jdbc.update("DELETE FROM TB_REMINDER_DELIVERY WHERE USER_SEQ=? AND SEND_DATE=?",id,Date.valueOf(today));
        } catch(MailSendException failure) {
            // SMTP 결과가 불명확하면 재시도하지 않는다.
            jdbc.update("UPDATE TB_REMINDER_DELIVERY SET STATUS='UNKNOWN' WHERE USER_SEQ=? AND SEND_DATE=?",id,Date.valueOf(today));
        } catch(Exception failure) {
            jdbc.update("UPDATE TB_REMINDER_DELIVERY SET STATUS='FAILED' WHERE USER_SEQ=? AND SEND_DATE=?",id,Date.valueOf(today));
        }
    }
}
