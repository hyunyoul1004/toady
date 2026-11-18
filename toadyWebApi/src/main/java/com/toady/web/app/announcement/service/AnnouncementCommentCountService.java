package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 반환할 페이지의 정확한 공고 키만 묶어 댓글 수를 한 번에 집계한다. 공고별 추가 쿼리를 만들지 않는다.
 * 공고마다 count 쿼리를 따로 날리면 목록 20건에 쿼리 20번이 나간다(N+1 문제). 여기서는 한 번으로 끝낸다.
 * 호출: AnnouncementSearchService.searchAll. 테이블: TB_LTTOT_PBLANC_COMMENT(읽기).
 */
// @Service: 스프링 빈으로 등록한다.
@Service
// @Transactional(readOnly = true): 조회 전용 트랜잭션. 변경 감지를 하지 않아 가볍다.
@Transactional(readOnly = true)
public class AnnouncementCommentCountService {
    // EntityManager: JPA의 기본 도구. 조건 개수가 매번 달라 저장소 메서드 대신 JPQL 문자열을 직접 만들어 실행한다.
    private final EntityManager entityManager;
    public AnnouncementCommentCountService(EntityManager entityManager) { this.entityManager = entityManager; }

    /**
     * 목록 항목마다 댓글 수를 채워 돌려준다.
     * (유형, 주택관리번호, 공고번호) 조합마다 "(… and … and …)" 조건을 만들어 or로 잇고, group by로 공고별 개수를 센다.
     * 값은 문자열에 넣지 않고 :type0 같은 이름 파라미터로 넘겨 JPQL 주입을 막는다. 댓글이 없는 공고는 0이다.
     */
    public List<AnnouncementListItem> enrich(List<AnnouncementListItem> items) {
        if (items.isEmpty()) return items;
        List<Key> keys = items.stream().map(Key::of).distinct().toList();
        List<String> clauses = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            clauses.add("(c.announcementType = :type" + index + " and c.houseManageNo = :house" + index + " and c.pblancNo = :notice" + index + ")");
        }
        var query = entityManager.createQuery("select c.announcementType, c.houseManageNo, c.pblancNo, count(c) "
                + "from AnnouncementCommentEntity c where c.deleteYn = 'N' and (" + String.join(" or ", clauses)
                + ") group by c.announcementType, c.houseManageNo, c.pblancNo", Object[].class);
        for (int index = 0; index < keys.size(); index++) {
            Key key = keys.get(index);
            query.setParameter("type" + index, key.type()).setParameter("house" + index, key.house())
                    .setParameter("notice" + index, key.notice());
        }
        Map<Key, Long> counts = new HashMap<>();
        for (Object[] row : query.getResultList()) {
            counts.put(new Key((String) row[0], (String) row[1], (String) row[2]), ((Number) row[3]).longValue());
        }
        return items.stream().map(item -> item.withCommentCount(counts.getOrDefault(Key.of(item), 0L))).toList();
    }
    // 공고를 구분하는 키. 댓글 테이블에는 유형이 name()(APT …)으로 저장되어 있어 경로값을 바꿔 맞춘다.
    private record Key(String type, String house, String notice) {
        static Key of(AnnouncementListItem item) {
            return new Key(AnnouncementType.fromPath(item.type()).name(), item.houseManageNo(), item.pblancNo());
        }
    }
}
