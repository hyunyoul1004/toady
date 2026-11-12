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

/** 반환할 페이지의 정확한 공고 키만 묶어 댓글 수를 한 번에 집계한다. 공고별 추가 쿼리를 만들지 않는다. */
@Service
@Transactional(readOnly = true)
public class AnnouncementCommentCountService {
    private final EntityManager entityManager;
    public AnnouncementCommentCountService(EntityManager entityManager) { this.entityManager = entityManager; }

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
    private record Key(String type, String house, String notice) {
        static Key of(AnnouncementListItem item) {
            return new Key(AnnouncementType.fromPath(item.type()).name(), item.houseManageNo(), item.pblancNo());
        }
    }
}
