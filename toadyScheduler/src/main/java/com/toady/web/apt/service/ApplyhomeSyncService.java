package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomePageCollector;
import com.toady.web.apt.entity.AptLttotPblancDetail;
import com.toady.web.apt.entity.AptLttotPblancMdl;
import com.toady.web.apt.entity.OptLttotPblancDetail;
import com.toady.web.apt.entity.OptLttotPblancMdl;
import com.toady.web.apt.entity.PblPvtRentLttotPblancDetail;
import com.toady.web.apt.entity.PblPvtRentLttotPblancMdl;
import com.toady.web.apt.entity.RemndrLttotPblancDetail;
import com.toady.web.apt.entity.RemndrLttotPblancMdl;
import com.toady.web.apt.entity.UrbtyOfctlLttotPblancDetail;
import com.toady.web.apt.entity.UrbtyOfctlLttotPblancMdl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 날짜 범위의 공고 상세와 연결된 주택형 정보를 청약홈에서 가져와 테이블별로 적재한다.
 */
@Service
public class ApplyhomeSyncService {

	private static final Logger log = LoggerFactory.getLogger(ApplyhomeSyncService.class);
	private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private final ApplyhomeApiClient apiClient;
	private final ApplyhomeEntityMapper mapper;
	private final ApplyhomeDataPersistenceService persistenceService;
	private final ApplyhomeSyncLock syncLock;

	public ApplyhomeSyncService(
			ApplyhomeApiClient apiClient,
			ApplyhomeEntityMapper mapper,
			ApplyhomeDataPersistenceService persistenceService,
			ApplyhomeSyncLock syncLock) {
		this.apiClient = apiClient;
		this.mapper = mapper;
		this.persistenceService = persistenceService;
		this.syncLock = syncLock;
	}

	/**
	 * 지정 기간의 다섯 분양 유형을 순서대로 동기화한다.
	 * 동일한 날짜 범위가 겹쳐 실행되지 않도록 전체 작업을 DB advisory lock으로 감싼다.
	 *
	 * @param startDate 모집공고일 조회 시작일(포함)
	 * @param endDate 모집공고일 조회 종료일(포함)
	 */
	public void synchronize(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("A valid start and end date range is required");
		}
		syncLock.execute(() -> {
			synchronizeDataset("getAPTLttotPblancDetail", AptLttotPblancDetail.class,
					"getAPTLttotPblancMdl", AptLttotPblancMdl.class, startDate, endDate, false, false);
			synchronizeDataset("getUrbtyOfctlLttotPblancDetail", UrbtyOfctlLttotPblancDetail.class,
					"getUrbtyOfctlLttotPblancMdl", UrbtyOfctlLttotPblancMdl.class, startDate, endDate, false, true);
			synchronizeDataset("getRemndrLttotPblancDetail", RemndrLttotPblancDetail.class,
					"getRemndrLttotPblancMdl", RemndrLttotPblancMdl.class, startDate, endDate, false, false);
			synchronizeDataset("getPblPvtRentLttotPblancDetail", PblPvtRentLttotPblancDetail.class,
					"getPblPvtRentLttotPblancMdl", PblPvtRentLttotPblancMdl.class, startDate, endDate, true, false);
			synchronizeDataset("getOPTLttotPblancDetail", OptLttotPblancDetail.class,
					"getOPTLttotPblancMdl", OptLttotPblancMdl.class, startDate, endDate, true, false);
		});
	}

	/**
	 * 한 유형의 상세 목록을 먼저 저장하고, 목록에서 얻은 공고 키로 주택형 API를 호출해 후속 저장한다.
	 * 민간임대/임의공급은 날짜 검색값 형식이 다르며, 도시형 주택형은 API 중복 행을 제거한다.
	 */
	private <D, M> void synchronizeDataset(
			String detailEndpoint,
			Class<D> detailType,
			String modelEndpoint,
			Class<M> modelType,
			LocalDate startDate,
			LocalDate endDate,
			boolean compactDate,
			boolean urbanModel) {
		Map<String, String> dateFilters = dateFilters(startDate, endDate, compactDate);
		List<JsonNode> detailRows = fetchAllPages(detailEndpoint, dateFilters);
		List<D> details = detailRows.stream().map(row -> mapper.toEntity(row, detailType)).toList();
		persistenceService.upsertAll(details);
		log.info("Upserted Applyhome endpoint={} rows={}", detailEndpoint, details.size());

		Set<AnnouncementKey> announcementKeys = new LinkedHashSet<>();
		for (JsonNode row : detailRows) {
			// 주택형 API는 날짜 범위 검색을 제공하지 않아 상세 목록의 복합 키로 호출한다.
			String houseManageNo = textValue(row, "HOUSE_MANAGE_NO");
			String pblancNo = textValue(row, "PBLANC_NO");
			if (houseManageNo == null || pblancNo == null) {
				throw new IllegalStateException("Applyhome detail response is missing announcement keys: " + detailEndpoint);
			}
			announcementKeys.add(new AnnouncementKey(houseManageNo, pblancNo));
		}

		List<M> models = new ArrayList<>();
		Set<JsonNode> distinctUrbanRows = new LinkedHashSet<>();
		// 주택형 조회에 실패한 공고는 이번 교체에서 빼고(기존 주택형 유지) 나머지 공고는 반영한 뒤 실패를 알린다.
		List<AnnouncementKey> fetched = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		for (AnnouncementKey key : announcementKeys) {
			Map<String, String> filters = Map.of(
					"cond[HOUSE_MANAGE_NO::EQ]", key.houseManageNo(),
					"cond[PBLANC_NO::EQ]", key.pblancNo());
			List<M> keyModels = new ArrayList<>();
			try {
				for (JsonNode row : fetchAllPages(modelEndpoint, filters)) {
	                if (!key.houseManageNo().equals(textValue(row, "HOUSE_MANAGE_NO"))
	                        || !key.pblancNo().equals(textValue(row, "PBLANC_NO"))) {
	                    throw new ResponseMismatchException("Applyhome model response does not match the requested announcement: " + modelEndpoint);
	                }
					// 도시형 응답에는 동일 주택형이 반복될 수 있어 원본 JSON 기준으로 한 번만 적재한다.
					if (urbanModel && !distinctUrbanRows.add(row)) {
						continue;
					}
					keyModels.add(mapper.toEntity(row, modelType));
				}
			} catch (RuntimeException failure) {
				// 다른 공고의 주택형이 섞여 오면 제공처 전체 이상으로 보고 아무것도 바꾸지 않고 멈춘다(개별 오류만 건너뛴다).
				if (failure instanceof ResponseMismatchException || Thread.currentThread().isInterrupted()) throw failure;
				failures.add(key.houseManageNo() + "/" + key.pblancNo());
				log.warn("Applyhome model sync failed for one announcement endpoint={} key={}/{} error={}",
						modelEndpoint, key.houseManageNo(), key.pblancNo(), failure.getMessage());
				if (failures.size() >= 20) throw new IllegalStateException("주택형 수집 실패가 20건을 넘어 중단했습니다: " + failure.getMessage(), failure);
				continue;
			}
			fetched.add(key);
			models.addAll(keyModels);
		}
		announcementKeys = new LinkedHashSet<>(fetched);

		if (urbanModel) {
			List<UrbtyOfctlLttotPblancMdl> urbanModels = models.stream()
					.map(UrbtyOfctlLttotPblancMdl.class::cast)
					.toList();
			persistenceService.upsertUrbanModels(List.copyOf(announcementKeys), urbanModels);
		} else {
			persistenceService.replaceModels(modelType, List.copyOf(announcementKeys), models);
		}
		log.info("Upserted Applyhome endpoint={} announcements={} rows={}",
				modelEndpoint, announcementKeys.size(), models.size());
		if (!failures.isEmpty()) {
			throw new IllegalStateException("주택형 수집 중 " + failures.size() + "건 실패(나머지는 저장됨): "
					+ String.join(", ", failures.subList(0, Math.min(5, failures.size()))) + (failures.size() > 5 ? " 외" : ""));
		}
	}

	/**
	 * matchCount에 도달할 때까지 페이지를 읽는다. 전체 건수가 바뀌거나 페이지가 비어 조기 종료되면 실패 처리한다.
	 */
	private List<JsonNode> fetchAllPages(String endpoint, Map<String, String> filters) {
		List<String> keys = endpoint.equals("getUrbtyOfctlLttotPblancMdl") ? null
                : endpoint.endsWith("Mdl") ? List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO")
                : List.of("HOUSE_MANAGE_NO", "PBLANC_NO");
        return ApplyhomePageCollector.fetchAll(endpoint, page -> apiClient.getPage(endpoint, page, filters), keys);
	}

	/**
	 * API 유형별로 요구하는 모집공고일 문자열 형식을 적용해 시작/종료 조건을 만든다.
	 */
	private Map<String, String> dateFilters(LocalDate startDate, LocalDate endDate, boolean compactDate) {
		DateTimeFormatter formatter = compactDate ? COMPACT_DATE : DateTimeFormatter.ISO_LOCAL_DATE;
		return Map.of(
				"cond[RCRIT_PBLANC_DE::GTE]", startDate.format(formatter),
				"cond[RCRIT_PBLANC_DE::LTE]", endDate.format(formatter));
	}

	/** 대문자 JSON 키를 우선 사용하고, 소문자 키로 내려오는 응답도 함께 처리한다. */
	private String textValue(JsonNode row, String fieldName) {
		JsonNode value = row.get(fieldName);
		if (value == null) {
			value = row.get(fieldName.toLowerCase());
		}
		return value == null || value.isNull() ? null : value.asText();
	}

	/** 요청한 공고와 다른 공고의 응답. 개별 공고 오류가 아니라 제공처 이상이므로 전체 수집을 멈춘다. */
	static final class ResponseMismatchException extends IllegalStateException {
		ResponseMismatchException(String message) { super(message); }
	}
}
