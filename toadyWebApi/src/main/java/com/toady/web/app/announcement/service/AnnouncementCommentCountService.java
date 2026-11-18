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
    // EntityManager: JPA의 기본 도구. 여러 키 조합 조건을 저장소 메서드로 표현하기 어려워 JPQL을 직접 실행한다.
    private final EntityManager entityManager;
    public AnnouncementCommentCountService(EntityManager entityManager) { this.entityManager = entityManager; }

    /** 한 번에 묻는 공고 수. 모자라면 마지막 키를 반복해 채워, 쿼리 문자열이 항상 같은 모양(실행 계획 캐시 1개)이 되게 한다. */
    static final int CHUNK = 50;
    /** CHUNK개 조건을 or로 이은 고정 JPQL. 결과 크기와 상관없이 같은 문자열이라 Hibernate 쿼리 계획 캐시에 한 번만 올라간다. */
    private static final String QUERY = buildQuery();
    private static String buildQuery() {
        List<String> clauses = new ArrayList<>();
        for (int index = 0; index < CHUNK; index++) {
            clauses.add("(c.announcementType = :type" + index + " and c.houseManageNo = :house" + index + " and c.pblancNo = :notice" + index + ")");
        }
        return "select c.announcementType, c.houseManageNo, c.pblancNo, count(c) "
                + "from AnnouncementCommentEntity c where c.deleteYn = 'N' and (" + String.join(" or ", clauses)
                + ") group by c.announcementType, c.houseManageNo, c.pblancNo";
    }

    /**
     * 목록 항목마다 댓글 수를 채워 돌려준다.
     * (유형, 주택관리번호, 공고번호) 조합을 CHUNK(50)개씩 나눠 고정 모양의 쿼리로 센다. 예전처럼 결과 수만큼 조건을 늘리면
     * 결과 크기마다 다른 JPQL이 생겨(최대 1000가지) 쿼리 계획 캐시가 불어난다. 마지막 묶음은 마지막 키를 반복해 채운다(같은 키라 결과는 같다).
     * 값은 문자열에 넣지 않고 :type0 같은 이름 파라미터로 넘겨 JPQL 주입을 막는다. 댓글이 없는 공고는 0이다.
     * 목록 20건이면 쿼리 1번, 1000건이면 20번이다(공고마다 1번인 N+1보다 훨씬 적다).
     */
    public List<AnnouncementListItem> enrich(List<AnnouncementListItem> items) {
        if (items.isEmpty()) return items;
        List<Key> keys = items.stream().map(Key::of).distinct().toList();
        Map<Key, Long> counts = new HashMap<>();
        for (int from = 0; from < keys.size(); from += CHUNK) {
            List<Key> chunk = keys.subList(from, Math.min(keys.size(), from + CHUNK));
            var query = entityManager.createQuery(QUERY, Object[].class);
            for (int index = 0; index < CHUNK; index++) {
                Key key = chunk.get(Math.min(index, chunk.size() - 1));
                query.setParameter("type" + index, key.type()).setParameter("house" + index, key.house())
                        .setParameter("notice" + index, key.notice());
            }
            for (Object[] row : query.getResultList()) {
                counts.put(new Key((String) row[0], (String) row[1], (String) row[2]), ((Number) row[3]).longValue());
            }
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
