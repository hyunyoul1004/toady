package com.toady.web.apt.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.toady.web.apt.entity.UrbtyOfctlLttotPblancMdl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API에서 받은 엔티티를 트랜잭션과 일정 크기의 flush 단위로 데이터베이스에 반영한다.
 */
@Service
public class ApplyhomeDataPersistenceService {

	private static final int FLUSH_BATCH_SIZE = 500;

	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * 엔티티의 식별자를 기준으로 신규 행은 삽입하고 기존 행은 갱신한다.
	 * 대량 적재 시 영속성 컨텍스트가 커지지 않도록 500건마다 비운다.
	 */
	@Transactional
	public <T> void upsertAll(List<T> entities) {
		for (int index = 0; index < entities.size(); index++) {
			entityManager.merge(entities.get(index));
			if ((index + 1) % FLUSH_BATCH_SIZE == 0) {
				entityManager.flush();
				entityManager.clear();
			}
		}
		entityManager.flush();
		entityManager.clear();
	}

	/** 공고별 전체 응답을 최신 스냅샷으로 반영한다. 변경 없는 행은 유지하고, 사라지거나 변경된 행은 같은 트랜잭션에서 교체한다. */
	@Transactional
	public void upsertUrbanModels(List<AnnouncementKey> announcements, List<UrbtyOfctlLttotPblancMdl> entities) {
		Map<AnnouncementKey, List<UrbtyOfctlLttotPblancMdl>> modelsByAnnouncement = new LinkedHashMap<>();
		for (AnnouncementKey key : announcements) {
			modelsByAnnouncement.put(key, new ArrayList<>());
		}
		for (UrbtyOfctlLttotPblancMdl model : entities) {
			AnnouncementKey key = new AnnouncementKey(model.getHouseManageNo(), model.getPblancNo());
			if (!modelsByAnnouncement.containsKey(key)) {
				throw new IllegalArgumentException("Model does not belong to a fetched announcement");
			}
			modelsByAnnouncement.get(key).add(model);
		}
		for (var entry : modelsByAnnouncement.entrySet()) {
			AnnouncementKey key = entry.getKey();
			if (entry.getValue().isEmpty()) {
				// 응답에 주택형이 하나도 없으면 일시적인 빈 응답일 수 있어 기존 주택형을 지우지 않는다(일반 주택형과 같은 규칙).
				org.slf4j.LoggerFactory.getLogger(ApplyhomeDataPersistenceService.class)
						.warn("주택형 응답이 비어 기존 주택형을 유지: type=UrbtyOfctlLttotPblancMdl, house={}, notice={}", key.houseManageNo(), key.pblancNo());
				continue;
			}
			List<UrbtyOfctlLttotPblancMdl> existing = entityManager.createQuery("""
					select model from UrbtyOfctlLttotPblancMdl model
					where model.houseManageNo = :houseManageNo and model.pblancNo = :pblancNo
					order by model.id desc
					""", UrbtyOfctlLttotPblancMdl.class)
					.setParameter("houseManageNo", key.houseManageNo())
					.setParameter("pblancNo", key.pblancNo()).getResultList();
			Map<UrbanModelValues, UrbtyOfctlLttotPblancMdl> known = new LinkedHashMap<>();
			for (var model : existing) {
				known.putIfAbsent(valuesOf(model), model);
			}
			Set<UrbanModelValues> received = new HashSet<>();
			for (var model : entry.getValue()) {
				if (!received.add(valuesOf(model))) continue;
				var current = known.get(valuesOf(model));
				if (current == null) {
					entityManager.persist(model);
					known.put(valuesOf(model), model);
				}
			}
			for (var model : existing) {
				// Remove obsolete values and legacy duplicate rows, keeping one unchanged ID.
				if (!received.contains(valuesOf(model)) || known.get(valuesOf(model)) != model) {
					entityManager.remove(model);
				}
			}
			// Commit atomically; bound the persistence context to a single announcement.
			entityManager.flush();
			entityManager.clear();
		}
	}

    /**
     * 일반 주택형도 공고별 전체 응답으로 교체해 삭제·모델번호 정정을 반영한다.
     * 응답에 주택형이 하나도 없는 공고는 일시적인 빈 응답일 수 있어 기존 주택형을 지우지 않는다.
     */
    @Transactional
    public <T> void replaceModels(Class<T> type, List<AnnouncementKey> announcements, List<T> models) {
        Set<Class<?>> allowed = Set.of(com.toady.web.apt.entity.AptLttotPblancMdl.class,
                com.toady.web.apt.entity.RemndrLttotPblancMdl.class,
                com.toady.web.apt.entity.PblPvtRentLttotPblancMdl.class,
                com.toady.web.apt.entity.OptLttotPblancMdl.class);
        if (!allowed.contains(type)) throw new IllegalArgumentException("Unsupported model entity");
        Set<AnnouncementKey> scopes = Set.copyOf(announcements);
        for (T model : models) {
            var bean = new org.springframework.beans.BeanWrapperImpl(model);
            var key = new AnnouncementKey((String) bean.getPropertyValue("houseManageNo"),
                    (String) bean.getPropertyValue("pblancNo"));
            if (!scopes.contains(key) || bean.getPropertyValue("modelNo") == null) {
                throw new IllegalArgumentException("Model is missing keys or belongs to another announcement");
            }
        }
        Set<AnnouncementKey> present = new java.util.HashSet<>();
        for (T model : models) {
            var bean = new org.springframework.beans.BeanWrapperImpl(model);
            present.add(new AnnouncementKey((String) bean.getPropertyValue("houseManageNo"), (String) bean.getPropertyValue("pblancNo")));
        }
        for (AnnouncementKey key : scopes) {
            if (!present.contains(key)) {
                org.slf4j.LoggerFactory.getLogger(ApplyhomeDataPersistenceService.class)
                        .warn("주택형 응답이 비어 기존 주택형을 유지: type={}, house={}, notice={}", type.getSimpleName(), key.houseManageNo(), key.pblancNo());
                continue;
            }
            entityManager.createQuery("delete from " + type.getSimpleName()
                    + " m where m.houseManageNo = :house and m.pblancNo = :notice")
                    .setParameter("house", key.houseManageNo()).setParameter("notice", key.pblancNo()).executeUpdate();
        }
        entityManager.clear();
        upsertAll(models);
    }

	private UrbanModelValues valuesOf(UrbtyOfctlLttotPblancMdl model) {
		return new UrbanModelValues(
				model.getModelNo(), model.getGp(), model.getTp(), model.getExcluseAr(),
				model.getSuplyHshldco(), model.getSuplyAmount(), model.getSubscrptReqstAmount());
	}

	private record UrbanModelValues(
			String modelNo,
			String gp,
			String tp,
			String excluseAr,
			String suplyHshldco,
			String suplyAmount,
			String subscrptReqstAmount) {
	}
}
