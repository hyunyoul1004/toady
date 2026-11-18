package com.toady.web.apt.service;

import com.toady.web.apt.entity.AptLttotPblancMdl;
import com.toady.web.apt.entity.OptLttotPblancMdl;
import com.toady.web.apt.entity.PblPvtRentLttotPblancMdl;
import com.toady.web.apt.entity.RemndrLttotPblancMdl;
import com.toady.web.apt.entity.UrbtyOfctlLttotPblancMdl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;
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
 * 공고 상세 5개 테이블(TB_*_LTTOT_PBLANC_DETAIL)과 주택형 5개 테이블(TB_*_LTTOT_PBLANC_MDL)에 쓴다.
 * 호출: ApplyhomeSyncService(공고·주택형 수집).
 */
// @Service: 업무 로직 빈. @Transactional이 붙은 public 메서드는 스프링 프록시를 거쳐 트랜잭션 안에서 실행된다.
@Service
public class ApplyhomeDataPersistenceService {

	private static final Logger LOG = LoggerFactory.getLogger(ApplyhomeDataPersistenceService.class);
	private static final int FLUSH_BATCH_SIZE = 500;
	/** 공고별 교체(replaceModels)를 허용하는 일반 주택형 엔티티. 도시형은 upsertUrbanModels가 따로 처리한다. */
	private static final Set<Class<?>> REPLACEABLE_MODELS = Set.of(AptLttotPblancMdl.class,
			RemndrLttotPblancMdl.class, PblPvtRentLttotPblancMdl.class, OptLttotPblancMdl.class);

	// @PersistenceContext: JPA의 EntityManager를 넣어 준다. 트랜잭션마다 알맞은 실제 객체로 연결해 주는 대리 객체다.
	@PersistenceContext
	private EntityManager entityManager;

	/**
	 * 엔티티의 식별자를 기준으로 신규 행은 삽입하고 기존 행은 갱신한다.
	 * 대량 적재 시 영속성 컨텍스트가 커지지 않도록 500건마다 비운다.
	 * merge는 기본키로 기존 행을 먼저 찾아 있으면 UPDATE, 없으면 INSERT한다(SQL의 ON DUPLICATE KEY와 같은 효과를 JPA로 낸다).
	 */
	// @Transactional: 메서드 전체를 한 트랜잭션으로 묶는다. 중간에 예외가 나면 이번 호출의 저장은 모두 되돌린다.
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
				LOG.warn("주택형 응답이 비어 기존 주택형을 유지: type=UrbtyOfctlLttotPblancMdl, house={}, notice={}", key.houseManageNo(), key.pblancNo());
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
				// 응답에서 사라진 값과 예전에 중복으로 쌓인 행을 지운다. 바뀌지 않은 값은 기존 ID 하나만 남긴다.
				if (!received.contains(valuesOf(model)) || known.get(valuesOf(model)) != model) {
					entityManager.remove(model);
				}
			}
			// 공고 하나마다 DB에 반영(flush)하고 영속성 컨텍스트를 비워 메모리를 아낀다. 커밋은 메서드가 끝날 때 한 번에 된다.
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
        if (!REPLACEABLE_MODELS.contains(type)) throw new IllegalArgumentException("Unsupported model entity");
        Set<AnnouncementKey> scopes = Set.copyOf(announcements);
        // 삭제 전에 모든 행의 키를 검증하고, 응답에 주택형이 있는 공고를 함께 모은다.
        Set<AnnouncementKey> present = new HashSet<>();
        for (T model : models) {
            var bean = new BeanWrapperImpl(model);
            var key = new AnnouncementKey((String) bean.getPropertyValue("houseManageNo"),
                    (String) bean.getPropertyValue("pblancNo"));
            if (!scopes.contains(key) || bean.getPropertyValue("modelNo") == null) {
                throw new IllegalArgumentException("Model is missing keys or belongs to another announcement");
            }
            present.add(key);
        }
        for (AnnouncementKey key : scopes) {
            if (!present.contains(key)) {
                LOG.warn("주택형 응답이 비어 기존 주택형을 유지: type={}, house={}, notice={}", type.getSimpleName(), key.houseManageNo(), key.pblancNo());
                continue;
            }
            entityManager.createQuery("delete from " + type.getSimpleName()
                    + " m where m.houseManageNo = :house and m.pblancNo = :notice")
                    .setParameter("house", key.houseManageNo()).setParameter("notice", key.pblancNo()).executeUpdate();
        }
        entityManager.clear();
        upsertAll(models);
    }

	/**
	 * 공고 하나의 상세와 주택형을 한 트랜잭션으로 저장한다. 둘 중 하나라도 실패하면 둘 다 되돌려 기존 값이 그대로 남는다.
	 * 주택형 규칙은 replaceModels·upsertUrbanModels와 같다(빈 응답이면 기존 주택형 유지).
	 * 같은 빈 안의 호출이라 내부 메서드의 @Transactional은 따로 적용되지 않고 이 트랜잭션 하나에 묶인다.
	 */
	@Transactional
	@SuppressWarnings("unchecked")
	public <M> void saveAnnouncement(Object detail, Class<M> modelType, AnnouncementKey key, List<M> models) {
		upsertAll(List.of(detail));
		if (modelType == UrbtyOfctlLttotPblancMdl.class) {
			upsertUrbanModels(List.of(key), (List<UrbtyOfctlLttotPblancMdl>) models);
		} else {
			replaceModels(modelType, List.of(key), models);
		}
	}

	/** 도시형 주택형을 '값이 같은지' 비교하기 위한 묶음을 만든다(ID 제외). */
	private UrbanModelValues valuesOf(UrbtyOfctlLttotPblancMdl model) {
		return new UrbanModelValues(
				model.getModelNo(), model.getGp(), model.getTp(), model.getExcluseAr(),
				model.getSuplyHshldco(), model.getSuplyAmount(), model.getSubscrptReqstAmount());
	}

	/** 도시형 주택형 비교용 값 묶음. record라 모든 필드로 equals·hashCode가 자동으로 만들어진다. */
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
