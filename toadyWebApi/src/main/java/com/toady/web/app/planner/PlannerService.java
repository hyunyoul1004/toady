package com.toady.web.app.planner;

import com.toady.web.app.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** 회원별 관심 공고의 키와 준비 상태만 저장한다. 제목과 일정은 조회할 때 원본에서 갱신한다. */
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
        catch(org.springframework.jdbc.BadSqlGrammarException missing) { return false; }
        return true;
    }
    public List<Favorite> list(long user) {
        boolean withStage=stageColumn();
        return jdbc.query("SELECT * FROM TB_FAVORITE WHERE USER_SEQ=? ORDER BY REG_DT DESC", (r,n)-> {
            String type=r.getString("ANNOUNCEMENT_TYPE"),house=r.getString("HOUSE_MANAGE_NO"),notice=r.getString("PBLANC_NO");
            PlannerSummary.Summary summary;
            try { summary=summaries.get(type,house,notice); }
            catch(EntityNotFoundException e) { summary=PlannerSummary.Summary.missing(type+"/"+house+"/"+notice); }
            return new Favorite(summary.id(),r.getString("NOTE"),r.getInt("CHECKLIST"),r.getBoolean("EMAIL_ENABLED"),withStage?r.getInt("STAGE"):0,summary);
        },user);
    }
    /** stage가 null이면 기존 단계를 그대로 둔다(단계 기능 이전 화면·클라이언트 호환). */
    @Transactional
    public void save(long user,String type,String house,String notice,String note,int checklist,boolean email,Integer stage) {
        var account=users.findById(user).orElseThrow(EntityNotFoundException::new);
        users.findByEmailForUpdate(account.getUserEmail()); // 수량 제한과 동시 저장을 계정 단위로 직렬화한다.
        summaries.get(type,house,notice);
        boolean exists=jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",Integer.class,user,type,house,notice)>0;
        boolean withStage=stage!=null && stageColumn();
        if(exists) {
            jdbc.update("UPDATE TB_FAVORITE SET NOTE=?,CHECKLIST=?,EMAIL_ENABLED=? WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",note,checklist,email,user,type,house,notice);
            if(withStage) jdbc.update("UPDATE TB_FAVORITE SET STAGE=? WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",stage,user,type,house,notice);
        } else {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=?",Integer.class,user)>=50) throw new IllegalArgumentException("관심 공고는 50개까지 저장할 수 있습니다.");
            jdbc.update("INSERT INTO TB_FAVORITE (USER_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,NOTE,CHECKLIST,EMAIL_ENABLED,REG_DT) VALUES (?,?,?,?,?,?,?,?)",user,type,house,notice,note,checklist,email,java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))));
            if(withStage) jdbc.update("UPDATE TB_FAVORITE SET STAGE=? WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",stage,user,type,house,notice);
        }
    }
    public void delete(long user,String type,String house,String notice) {
        jdbc.update("DELETE FROM TB_FAVORITE WHERE USER_SEQ=? AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",user,type,house,notice);
    }
    public void disableEmails(long user) { jdbc.update("UPDATE TB_FAVORITE SET EMAIL_ENABLED=FALSE WHERE USER_SEQ=?",user); }
}
