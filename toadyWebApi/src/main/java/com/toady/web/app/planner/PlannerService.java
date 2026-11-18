package com.toady.web.app.planner;

import com.toady.web.app.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 나의 청약(관심 공고) 저장소. TB_FAVORITE 테이블을 JdbcTemplate(SQL 직접 실행)으로 읽고 쓴다.
 * 회원별 관심 공고의 키와 준비 상태만 저장한다. 제목과 일정은 조회할 때 원본에서 갱신한다(PlannerSummary).
 * PlannerController, PlannerReminder(메일 알림), ReminderUnsubscribe(수신 해제), CalendarFeed가 쓴다.
 */
// @Service: 비즈니스 로직 빈으로 등록한다. 생성자가 하나라 스프링이 JdbcTemplate 등을 자동으로 넣어 준다.
@Service
public class PlannerService {
    private final JdbcTemplate jdbc; private final UserRepository users; private final PlannerSummary summaries;
    public PlannerService(JdbcTemplate jdbc,UserRepository users,PlannerSummary summaries) { this.jdbc=jdbc;this.users=users;this.summaries=summaries; }
    /** stage: 0 관심 · 1 신청 예정 · 2 신청 완료 · 3 당첨 · 4 미당첨 · 5 계약 완료 (database.sql 16번). */
    public record Favorite(String id,String note,int checklist,boolean emailEnabled,int stage,PlannerSummary.Summary summary) { }
    public static final int MAX_STAGE=5;
    /** STAGE 컬럼이 있으면 true로 기억한다. 없으면 매번 가볍게 다시 확인해 SQL 적용 직후부터 저장한다. */
    private volatile boolean stageColumn;
    boolean stageColumn() {
        if(stageColumn) return true;
        try { jdbc.queryForList("SELECT STAGE FROM TB_FAVORITE WHERE 1=0"); stageColumn=true; }
        catch(BadSqlGrammarException missing) { return false; }
        return true;
    }
    /** 운영자가 숨긴 공고. 관심 공고 목록(화면·메일 알림·캘린더)에서 뺀다. 빈이 없으면(일부 테스트) 숨김 없이 동작한다. */
    // @Autowired(required=false): 빈이 있을 때만 넣는다.
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    HiddenAnnouncements hidden;
    /** TB_FAVORITE(유형 경로값 'apt' 형식)에서 숨긴 공고를 빼는 조건. */
    private HiddenAnnouncements.SqlFilter notHidden() {
        return hidden==null?HiddenAnnouncements.SqlFilter.NONE:hidden.notHiddenSql("ANNOUNCEMENT_TYPE","HOUSE_MANAGE_NO","PBLANC_NO",HiddenAnnouncements.TypeFormat.PATH);
    }
    /**
     * 내 관심 공고를 최근 저장 순으로. 원본 공고가 지워졌으면 '원본을 찾을 수 없는 공고'로 채워 목록에서 빠지지 않게 한다.
     * 운영자가 숨긴 공고는 뺀다(내 정보 요약의 관심 공고 수 AccountService.summary와 같은 조건).
     */
    public List<Favorite> list(long user) {
        boolean withStage=stageColumn();
        var notHidden=notHidden();
        return jdbc.query("SELECT * FROM TB_FAVORITE WHERE USER_SEQ=?"+notHidden.sql()+" ORDER BY REG_DT DESC", (r,n)-> {
            String type=r.getString("ANNOUNCEMENT_TYPE"),house=r.getString("HOUSE_MANAGE_NO"),notice=r.getString("PBLANC_NO");
            PlannerSummary.Summary summary;
            try { summary=summaries.get(type,house,notice); }
            catch(EntityNotFoundException e) { summary=PlannerSummary.Summary.missing(type+"/"+house+"/"+notice); }
            return new Favorite(summary.id(),r.getString("NOTE"),r.getInt("CHECKLIST"),r.getBoolean("EMAIL_ENABLED"),withStage?r.getInt("STAGE"):0,summary);
        },notHidden.with(user));
    }
    /** stage가 null이면 기존 단계를 그대로 둔다(단계 기능 이전 화면·클라이언트 호환). */
    // @Transactional: 메서드 안의 SQL을 하나의 트랜잭션으로 묶는다. 중간에 예외가 나면 모두 되돌린다.
    // 아래 계정 행 잠금(FOR UPDATE)도 트랜잭션이 끝날 때까지 유지된다.
    @Transactional
    public void save(long user,String type,String house,String notice,String note,int checklist,boolean email,Integer stage) {
        var account=users.findById(user).orElseThrow(EntityNotFoundException::new);
        users.findByEmailForUpdate(account.getUserEmail()); // 수량 제한과 동시 저장을 계정 단위로 직렬화한다.
        boolean exists=jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",Integer.class,user,type,house,notice)>0;
        // 원본 공고 확인은 새로 추가할 때만 한다. 이미 저장한 공고는 원본이 사라져도 준비 노트·단계를 계속 고칠 수 있다.
        if(!exists) summaries.get(type,house,notice);
        boolean withStage=stage!=null && stageColumn();
        if(exists) {
            jdbc.update("UPDATE TB_FAVORITE SET NOTE=?,CHECKLIST=?,EMAIL_ENABLED=? WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",note,checklist,email,user,type,house,notice);
            if(withStage) jdbc.update("UPDATE TB_FAVORITE SET STAGE=? WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",stage,user,type,house,notice);
        } else {
            // 50개 한도는 화면에 보이는 관심 공고(숨긴 공고 제외, list와 같은 조건)로 센다.
            var visible=notHidden();
            if(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=?"+visible.sql(),Integer.class,visible.with(user))>=50) throw new PlannerLimitException("관심 공고는 50개까지 저장할 수 있어요. 지난 공고를 정리한 뒤 다시 저장해 주세요.");
            jdbc.update("INSERT INTO TB_FAVORITE (USER_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,NOTE,CHECKLIST,EMAIL_ENABLED,REG_DT) VALUES (?,?,?,?,?,?,?,?)",user,type,house,notice,note,checklist,email,Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul"))));
            if(withStage) jdbc.update("UPDATE TB_FAVORITE SET STAGE=? WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",stage,user,type,house,notice);
        }
    }
    /** 관심 공고 하나 삭제. 없는 항목이어도 오류 없이 지나간다. */
    public void delete(long user,String type,String house,String notice) {
        jdbc.update("DELETE FROM TB_FAVORITE WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",user,type,house,notice);
    }
    /** 이 회원의 모든 관심 공고 메일 알림을 끈다(메뉴의 일괄 해제, 메일 수신 해제 링크). */
    public void disableEmails(long user) { jdbc.update("UPDATE TB_FAVORITE SET EMAIL_ENABLED=FALSE WHERE USER_SEQ=?",user); }
}
