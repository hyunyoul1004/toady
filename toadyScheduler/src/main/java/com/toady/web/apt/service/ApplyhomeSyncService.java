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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 날짜 범위의 공고 상세와 연결된 주택형 정보를 청약홈에서 가져와 테이블별로 적재한다.
 * 다섯 유형: APT, 오피스텔·도시형, APT 잔여세대, 공공지원 민간임대, 임의공급.
 * 청약홈 API: ApplyhomeInfoDetailSvc의 get*LttotPblancDetail(공고), get*LttotPblancMdl(주택형).
 * 호출: ApplyhomeSyncScheduler(고정 일정), AdminJobService(관리자 수동·자동 실행).
 */
// @Service: 업무 로직 빈으로 등록한다.
@Service
public class ApplyhomeSyncService {

	private static final Logger log = LoggerFactory.getLogger(ApplyhomeSyncService.class);
	private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	/** 이보다 많이 실패하면 DB·제공처 같은 공통 장애로 보고 나머지를 멈춘다(다섯 유형 전체 기준). */
	static final int MAX_FAILURES = 20;

	private final ApplyhomeApiClient apiClient;
	private final ApplyhomeEntityMapper mapper;
	private final ApplyhomeDataPersistenceService persistenceService;
	private final ApplyhomeSyncLock syncLock;

	/** 생성자 주입. 생성자가 하나뿐이면 @Autowired 없이도 스프링이 이 생성자로 빈을 넣어 준다. */
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
			// 한 유형에서 일부 공고의 상세·주택형 저장이 실패해도 나머지 공고·유형은 계속 수집하고, 실패는 끝에서 한 번에 알린다(한도 20건은 전체 기준).
			List<String> failures = new ArrayList<>();
			synchronizeDataset("getAPTLttotPblancDetail", AptLttotPblancDetail.class,
					"getAPTLttotPblancMdl", AptLttotPblancMdl.class, startDate, endDate, false, false, failures);
			synchronizeDataset("getUrbtyOfctlLttotPblancDetail", UrbtyOfctlLttotPblancDetail.class,
					"getUrbtyOfctlLttotPblancMdl", UrbtyOfctlLttotPblancMdl.class, startDate, endDate, false, true, failures);
			synchronizeDataset("getRemndrLttotPblancDetail", RemndrLttotPblancDetail.class,
					"getRemndrLttotPblancMdl", RemndrLttotPblancMdl.class, startDate, endDate, false, false, failures);
			synchronizeDataset("getPblPvtRentLttotPblancDetail", PblPvtRentLttotPblancDetail.class,
					"getPblPvtRentLttotPblancMdl", PblPvtRentLttotPblancMdl.class, startDate, endDate, true, false, failures);
			synchronizeDataset("getOPTLttotPblancDetail", OptLttotPblancDetail.class,
					"getOPTLttotPblancMdl", OptLttotPblancMdl.class, startDate, endDate, true, false, failures);
			if (!failures.isEmpty()) {
				throw new PartialFailureException("공고·주택형 수집 중 " + failures.size() + "건 실패(나머지는 저장됨): "
						+ String.join(", ", failures.subList(0, Math.min(5, failures.size()))) + (failures.size() > 5 ? " 외" : ""));
			}
		});
	}

	/**
	 * 한 유형의 상세 목록을 받은 뒤, 공고 하나씩 주택형 API를 호출하고 상세·주택형을 한 트랜잭션으로 저장한다.
	 * 저장은 공고마다 따로 트랜잭션을 연다. 한 공고의 값 오류(예: 컬럼 길이 초과)가 같은 유형의 다른 공고나
	 * 다음 유형·다음 기간까지 되돌리지 않게 하고, 실패한 공고 키만 failures에 모아 끝에서 일부 실패로 알린다.
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
			boolean urbanModel,
			List<String> failures) {
		String label = modelEndpoint.replace("get", "").replace("LttotPblancMdl", "");
		Map<String, String> dateFilters = dateFilters(startDate, endDate, compactDate);
		List<JsonNode> detailRows = fetchAllPages(detailEndpoint, dateFilters);

		// 저장 전에 모든 행의 공고 키를 확인한다. 키가 빠진 응답은 제공처 이상으로 보고 아무것도 바꾸지 않고 멈춘다.
		Map<AnnouncementKey, JsonNode> detailsByKey = new LinkedHashMap<>();
		for (JsonNode row : detailRows) {
			// 주택형 API는 날짜 범위 검색을 제공하지 않아 상세 목록의 복합 키로 호출한다.
			String houseManageNo = textValue(row, "HOUSE_MANAGE_NO");
			String pblancNo = textValue(row, "PBLANC_NO");
			if (houseManageNo == null || pblancNo == null) {
				throw new IllegalStateException("Applyhome detail response is missing announcement keys: " + detailEndpoint);
			}
			detailsByKey.put(new AnnouncementKey(houseManageNo, pblancNo), row);
		}

		// 공고 하나씩 주택형을 먼저 받고, 상세와 주택형을 한 트랜잭션으로 함께 저장한다.
		// 새 공고가 주택형 없이 먼저 보이거나, 주택형 조회·저장이 실패한 공고가 상세만 남는 일이 없게 하기 위해서다.
		// 실패한 공고는 상세·주택형 모두 이번에 바꾸지 않고(기존 값 유지) failures에 남긴다. 호출 수는 공고당 주택형 1회로 전과 같다.
		Set<JsonNode> distinctUrbanRows = new LinkedHashSet<>();
		int savedAnnouncements = 0;
		long savedModels = 0;
		for (var entry : detailsByKey.entrySet()) {
			AnnouncementKey key = entry.getKey();
			List<M> keyModels;
			try {
				keyModels = fetchModels(modelEndpoint, modelType, key, urbanModel, distinctUrbanRows);
				persistenceService.saveAnnouncement(mapper.toEntity(entry.getValue(), detailType), modelType, key, keyModels);
			} catch (RuntimeException failure) {
				recordFailure(failures, label, key, modelEndpoint, failure);
				continue;
			}
			savedAnnouncements++;
			savedModels += keyModels.size();
		}
		log.info("Upserted Applyhome endpoint={} announcements={} failed={} models={}",
				detailEndpoint, savedAnnouncements, detailsByKey.size() - savedAnnouncements, savedModels);
	}

	/**
	 * 공고 하나의 주택형을 모두 받아 엔티티로 바꾼다. 다른 공고의 응답이 섞여 오면 제공처 이상으로 보고 전체를 멈춘다.
	 * 도시형 응답에는 동일 주택형이 반복될 수 있어 원본 JSON 기준으로 한 번만 담는다.
	 */
	private <M> List<M> fetchModels(String modelEndpoint, Class<M> modelType, AnnouncementKey key,
			boolean urbanModel, Set<JsonNode> distinctUrbanRows) {
		Map<String, String> filters = Map.of(
				"cond[HOUSE_MANAGE_NO::EQ]", key.houseManageNo(),
				"cond[PBLANC_NO::EQ]", key.pblancNo());
		List<M> models = new ArrayList<>();
		for (JsonNode row : fetchAllPages(modelEndpoint, filters)) {
			if (!key.houseManageNo().equals(textValue(row, "HOUSE_MANAGE_NO"))
					|| !key.pblancNo().equals(textValue(row, "PBLANC_NO"))) {
				throw new ResponseMismatchException("Applyhome model response does not match the requested announcement: " + modelEndpoint);
			}
			if (urbanModel && !distinctUrbanRows.add(row)) {
				continue;
			}
			models.add(mapper.toEntity(row, modelType));
		}
		return models;
	}

	/**
	 * 공고 하나의 실패를 기록하고 넘어간다. 다만 아래 경우는 남은 공고도 똑같이 실패하거나 제공처 이상이므로 바로 멈춘다:
	 * 다른 공고의 응답이 섞여 온 경우, 중단 요청, 인증키 오류·일일 한도 초과, 실패가 MAX_FAILURES에 이른 경우.
	 */
	private void recordFailure(List<String> failures, String label, AnnouncementKey key, String endpoint, RuntimeException failure) {
		if (failure instanceof ResponseMismatchException || Thread.currentThread().isInterrupted()) throw failure;
		if (failure instanceof ApplyhomeApiClient.ProviderRejectedException) throw failure;
		failures.add(label + ":" + key.houseManageNo() + "/" + key.pblancNo());
		log.warn("Applyhome sync failed for one announcement endpoint={} key={}/{} error={}",
				endpoint, key.houseManageNo(), key.pblancNo(), failure.getMessage());
		if (failures.size() >= MAX_FAILURES) {
			throw new ApplyhomeFailureLimitException("공고·주택형 수집", MAX_FAILURES, failures, failure);
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

	/** 일부 공고의 상세·주택형만 실패했고 나머지는 저장된 경우(공통 ApplyhomePartialFailureException). */
	public static class PartialFailureException extends ApplyhomePartialFailureException {
		public PartialFailureException(String message) { super(message); }
	}
}
