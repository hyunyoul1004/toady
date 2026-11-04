package com.toady.web.apt.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import com.toady.web.apt.entity.UrbtyOfctlLttotPblancMdl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/**
	 * 기존 복합 키로 식별할 수 없는 도시형 주택형 데이터를 공고별로 비교해 신규 내용만 저장한다.
	 * DB 계정에 DELETE 권한이 없어 삭제 후 재삽입 대신, API가 반복 반환하는 동일 행을 값으로 판별한다.
	 */
	@Transactional
	public void upsertUrbanModels(List<UrbtyOfctlLttotPblancMdl> entities) {
		Map<AnnouncementKey, List<UrbtyOfctlLttotPblancMdl>> modelsByAnnouncement = new LinkedHashMap<>();
		for (UrbtyOfctlLttotPblancMdl model : entities) {
			AnnouncementKey key = new AnnouncementKey(model.getHouseManageNo(), model.getPblancNo());
			modelsByAnnouncement.computeIfAbsent(key, ignored -> new ArrayList<>()).add(model);
		}

		int persistedCount = 0;
		for (Map.Entry<AnnouncementKey, List<UrbtyOfctlLttotPblancMdl>> entry : modelsByAnnouncement.entrySet()) {
			AnnouncementKey key = entry.getKey();
			List<UrbtyOfctlLttotPblancMdl> existingModels = entityManager.createQuery("""
					select model from UrbtyOfctlLttotPblancMdl model
					where model.houseManageNo = :houseManageNo and model.pblancNo = :pblancNo
					""", UrbtyOfctlLttotPblancMdl.class)
					.setParameter("houseManageNo", key.houseManageNo())
					.setParameter("pblancNo", key.pblancNo())
					.getResultList();
			Set<UrbanModelValues> knownValues = new HashSet<>();
			for (UrbtyOfctlLttotPblancMdl model : existingModels) {
				knownValues.add(valuesOf(model));
			}

			for (UrbtyOfctlLttotPblancMdl model : entry.getValue()) {
				if (knownValues.add(valuesOf(model))) {
					entityManager.persist(model);
					persistedCount++;
				}
				if (persistedCount % FLUSH_BATCH_SIZE == 0 && persistedCount > 0) {
					entityManager.flush();
					entityManager.clear();
				}
			}
		}
		entityManager.flush();
		entityManager.clear();
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
