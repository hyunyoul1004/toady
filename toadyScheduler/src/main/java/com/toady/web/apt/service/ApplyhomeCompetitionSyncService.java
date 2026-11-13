package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomePageCollector;
import com.toady.web.apt.api.ApplyhomeCompetitionApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 공고별 경쟁률, 당첨가점, 특별공급 신청현황을 수집한다. */
@Service
public class ApplyhomeCompetitionSyncService {

	private static final Logger log = LoggerFactory.getLogger(ApplyhomeCompetitionSyncService.class);
	private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private static final TableSpec APT_RATE = new TableSpec("getAPTLttotPblancCmpet",
			"TB_APT_LTTOT_PBLANC_CMPET",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "SUBSCRPT_RANK_CODE", "RESIDE_SECD",
					"HOUSE_TY", "SUPLY_HSHLDCO", "RESIDE_SENM", "REQ_CNT", "CMPET_RATE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "SUBSCRPT_RANK_CODE", "RESIDE_SECD"));
	private static final TableSpec URBAN_RATE = new TableSpec("getUrbtyOfctlLttotPblancCmpet",
			"TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "HOUSE_TY", "SUPLY_HSHLDCO",
					"RESIDNT_PRIOR_AT", "RESIDENT_PRIOR_SENM", "REQ_CNT", "CMPET_RATE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO"));
	private static final TableSpec PRIVATE_RENT_RATE = new TableSpec("getPblPvtRentLttotPblancCmpet",
			"TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "SPSPLY_KND_CODE", "HOUSE_TY",
					"SUPLY_HSHLDCO", "SPSPLY_KND_NM", "SPSPLY_KND_HSHLDCO", "REQ_CNT", "CMPET_RATE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "SPSPLY_KND_CODE"));
	private static final TableSpec CANCELLED_RATE = new TableSpec("getCancResplLttotPblancCmpet",
			"TB_CANC_RESPL_LTTOT_PBLANC_CMPET",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "HOUSE_TY", "SUPLY_HSHLDCO",
					"NORMAL_HSHLDCO", "MNYCH_HSHLDCO", "NWWDS_HSHLDCO", "LFE_FRST_HSHLDCO",
					"OLD_PARNTS_SUPORT_HSHLDCO", "INSTT_RECOMEND_HSHLDCO", "NORMAL_REQ_CNT",
					"MNYCH_REQ_CNT", "NWWDS_REQ_CNT", "LFE_FRST_REQ_CNT", "OLD_PARNTS_SUPORT_REQ_CNT",
					"INSTT_RECOMEND_REQ_CNT", "NORMAL_CMPET_RATE", "MNYCH_CMPET_RATE", "NWWDS_CMPET_RATE",
					"LFE_FRST_CMPET_RATE", "OLD_PARNTS_SUPORT_CMPET_RATE", "INSTT_RECOMEND_CMPET_RATE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO"));
	private static final TableSpec REMNANT_RATE = new TableSpec("getRemndrLttotPblancCmpet",
			"TB_REMNDR_LTTOT_PBLANC_CMPET",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_TY", "REMNDR_HSHLD_PBLANC_TYCD",
					"SUPLY_HSHLDCO", "REQ_CNT", "CMPET_RATE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_TY", "REMNDR_HSHLD_PBLANC_TYCD"));
	private static final TableSpec APT_SCORE = new TableSpec("getAptLttotPblancScore",
			"TB_APT_LTTOT_PBLANC_SCORE",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "RESIDE_SECD", "HOUSE_TY",
					"RESIDE_SENM", "LWET_SCORE", "TOP_SCORE", "AVRG_SCORE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "RESIDE_SECD"));
	private static final TableSpec OPTIONAL_RATE = new TableSpec("getOPTLttotPblancCmpet",
			"TB_OPT_LTTOT_PBLANC_CMPET",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_TY", "SUPLY_HSHLDCO", "REQ_CNT", "CMPET_RATE"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_TY"));
	private static final TableSpec SPECIAL_SUPPLY_STATUS = new TableSpec("getAPTSpsplyReqstStus",
			"TB_APT_SPSPLY_REQST_STUS",
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_TY", "SPSPLY_HSHLDCO", "MNYCH_HSHLDCO",
					"NWWDS_NMTW_HSHLDCO", "LFE_FRST_HSHLDCO", "YGMN_HSHLDCO", "OLD_PARNTS_SUPORT_HSHLDCO",
					"NWBB_NWBBSHR_HSHLDCO", "INSTT_RECOMEND_HSHLDCO", "TRANSR_INSTT_ENFSN_HSHLDCO",
					"CRSPAREA_MNYCH_CNT", "CTPRVN_MNYCH_CNT", "ETC_AREA_MNYCH_CNT",
					"CRSPAREA_NWWDS_NMTW_CNT", "CTPRVN_NWWDS_NMTW_CNT", "ETC_AREA_NWWDS_NMTW_CNT",
					"CRSPAREA_LFE_FRST_CNT", "CTPRVN_LFE_FRST_CNT", "ETC_AREA_LFE_FRST_CNT",
					"CRSPAREA_YGMN_CNT", "CTPRVN_YGMN_CNT", "ETC_AREA_YGMN_CNT",
					"CRSPAREA_OPS_CNT", "CTPRVN_OPS_CNT", "ETC_AREA_OPS_CNT",
					"CRSPAREA_NWBB_NWBBSHR_CNT", "CTPRVN_NWBB_NWBBSHR_CNT", "ETC_AREA_NWBB_NWBBSHR_CNT",
					"INSTT_RECOMEND_DCSN_CNT", "INSTT_RECOMEND_PREPAR_CNT", "TRANSR_INSTT_ENFSN_CNT",
					"SUBSCRPT_RESULT_NM"),
			List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "HOUSE_TY"));

	private final ApplyhomeApiClient announcementApiClient;
	private final ApplyhomeCompetitionApiClient competitionApiClient;
	private final ApplyhomeCompetitionPersistenceService persistenceService;
	private final ApplyhomeSyncLock syncLock;

	public ApplyhomeCompetitionSyncService(
			ApplyhomeApiClient announcementApiClient,
			ApplyhomeCompetitionApiClient competitionApiClient,
			ApplyhomeCompetitionPersistenceService persistenceService,
			ApplyhomeSyncLock syncLock) {
		this.announcementApiClient = announcementApiClient;
		this.competitionApiClient = competitionApiClient;
		this.persistenceService = persistenceService;
		this.syncLock = syncLock;
	}

	public void synchronize(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("A valid start and end date range is required");
		}
		syncLock.execute(() -> synchronizeLocked(startDate, endDate));
	}

	/** 과거 가점만 재수집할 때 불필요한 경쟁률 API 호출을 피한다. 기간은 모집공고일 기준이다. */
	// 반환값은 조회해 upsert한 응답 행 수다. 같은 키를 갱신한 행도 포함한다.
	public long synchronizeScores(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("A valid start and end date range is required");
		}
		var count = new java.util.concurrent.atomic.AtomicLong();
		List<String> failures = new ArrayList<>();
		syncLock.execute(() -> count.set(syncForAnnouncements(APT_SCORE,
				discover("getAPTLttotPblancDetail", startDate, endDate, false), failures)));
		if (!failures.isEmpty()) throw new ApplyhomePartialFailureException("가점 수집 중 " + failures.size() + "건 실패(나머지는 저장됨): " + String.join(", ", failures.subList(0, Math.min(5, failures.size()))), count.get());
		return count.get();
	}

	private void synchronizeLocked(LocalDate startDate, LocalDate endDate) {
		List<AnnouncementKey> aptAnnouncements = discover("getAPTLttotPblancDetail", startDate, endDate, false);
		List<AnnouncementKey> urbanAnnouncements = discover(
				"getUrbtyOfctlLttotPblancDetail", startDate, endDate, false);
		List<AnnouncementKey> remnantAnnouncements = discover(
				"getRemndrLttotPblancDetail", startDate, endDate, false, "04");
		List<AnnouncementKey> cancelledAnnouncements = discover(
				"getRemndrLttotPblancDetail", startDate, endDate, false, "06");
		List<AnnouncementKey> privateRentAnnouncements = discover(
				"getPblPvtRentLttotPblancDetail", startDate, endDate, true);
		List<AnnouncementKey> optionalAnnouncements = discover("getOPTLttotPblancDetail", startDate, endDate, true);

		// 공고 하나의 실패(키 누락·중복·일시 오류)가 나머지 공고·유형의 수집을 막지 않게 끝까지 진행하고, 실패는 모아서 알린다.
		List<String> failures = new ArrayList<>();
		syncForAnnouncements(APT_RATE, aptAnnouncements, failures);
		syncForAnnouncements(CANCELLED_RATE, cancelledAnnouncements, failures);
		syncForAnnouncements(APT_SCORE, aptAnnouncements, failures);
		syncForAnnouncements(SPECIAL_SUPPLY_STATUS, aptAnnouncements, failures);
		syncForAnnouncements(URBAN_RATE, urbanAnnouncements, failures);
		syncForAnnouncements(REMNANT_RATE, remnantAnnouncements, failures);
		syncForAnnouncements(PRIVATE_RENT_RATE, privateRentAnnouncements, failures);
		syncForAnnouncements(OPTIONAL_RATE, optionalAnnouncements, failures);
		if (!failures.isEmpty()) {
			throw new ApplyhomePartialFailureException("경쟁률 수집 중 " + failures.size() + "건 실패(나머지는 저장됨): "
					+ String.join(", ", failures.subList(0, Math.min(5, failures.size()))) + (failures.size() > 5 ? " 외" : ""));
		}
	}
	/** 이보다 많이 실패하면 인증·쿼터 같은 공통 장애로 보고 나머지 호출을 멈춘다. */
	static final int MAX_FAILURES = 20;

	private List<AnnouncementKey> discover(
			String endpoint, LocalDate startDate, LocalDate endDate, boolean compactDate) {
		return discover(endpoint, startDate, endDate, compactDate, null);
	}

	private List<AnnouncementKey> discover(
			String endpoint, LocalDate startDate, LocalDate endDate, boolean compactDate, String houseSecd) {
		DateTimeFormatter formatter = compactDate ? COMPACT_DATE : DateTimeFormatter.ISO_LOCAL_DATE;
		Map<String, String> filters = new LinkedHashMap<>(Map.of(
				"cond[RCRIT_PBLANC_DE::GTE]", startDate.format(formatter),
				"cond[RCRIT_PBLANC_DE::LTE]", endDate.format(formatter)));
		if (houseSecd != null) filters.put("cond[HOUSE_SECD::EQ]", houseSecd);
		List<JsonNode> rows = fetchAllPages(announcementApiClient, endpoint, filters);
		Set<AnnouncementKey> keys = new LinkedHashSet<>();
		for (JsonNode row : rows) {
			JsonNode houseManageNo = field(row, "HOUSE_MANAGE_NO");
			JsonNode pblancNo = field(row, "PBLANC_NO");
			if (houseManageNo == null || houseManageNo.isNull() || pblancNo == null || pblancNo.isNull()) {
				throw new IllegalStateException("Applyhome announcement response is missing keys: " + endpoint);
			}
			keys.add(new AnnouncementKey(houseManageNo.asText(), pblancNo.asText()));
		}
		log.info("Discovered Applyhome announcements endpoint={} count={}", endpoint, keys.size());
		return List.copyOf(keys);
	}

	private long syncForAnnouncements(TableSpec table, List<AnnouncementKey> announcements, List<String> failures) {
		long totalRows = 0;
		for (AnnouncementKey key : announcements) {
			Map<String, String> filters = Map.of(
					"cond[HOUSE_MANAGE_NO::EQ]", key.houseManageNo(),
					"cond[PBLANC_NO::EQ]", key.pblancNo());
			try {
				List<JsonNode> rows = fetchAllPages(competitionApiClient, table, filters);
				persistenceService.replace(table.table(), table.columns(), table.keys(),
	                    key.houseManageNo(), key.pblancNo(), rows);
				totalRows += rows.size();
			} catch (RuntimeException failure) {
				// 중단 요청(배포·관리자 중지)은 그대로 멈춘다.
				if (Thread.currentThread().isInterrupted()) throw failure;
				// 인증키 오류·일일 한도 초과는 남은 공고도 모두 실패하므로 바로 멈추고 원인을 남긴다.
				if (failure instanceof com.toady.web.apt.api.ApplyhomeApiClient.ProviderRejectedException) throw failure;
				failures.add(table.table() + ":" + key.houseManageNo() + "/" + key.pblancNo());
				log.warn("Applyhome competition sync failed for one announcement table={} key={}/{} error={}",
						table.table(), key.houseManageNo(), key.pblancNo(), failure.getMessage());
				if (failures.size() >= MAX_FAILURES) throw new IllegalStateException("경쟁률 수집 실패가 " + MAX_FAILURES + "건을 넘어 중단했습니다: " + failure.getMessage(), failure);
			}
		}
		log.info("Synchronized Applyhome endpoint={} announcements={} rows={}",
				table.endpoint(), announcements.size(), totalRows);
		return totalRows;
	}

	private List<JsonNode> fetchAllPages(ApplyhomeApiClient client, String endpoint, Map<String, String> filters) {
		return ApplyhomePageCollector.fetchAll(endpoint, page -> client.getPage(endpoint, page, filters),
                List.of("HOUSE_MANAGE_NO", "PBLANC_NO"));
	}

	private List<JsonNode> fetchAllPages(ApplyhomeCompetitionApiClient client, TableSpec table, Map<String, String> filters) {
		return ApplyhomePageCollector.fetchAll(table.endpoint(), page -> client.getPage(table.endpoint(), page, filters), table.keys());
	}

	private JsonNode field(JsonNode row, String name) {
		JsonNode value = row.get(name);
		return value != null ? value : row.get(name.toLowerCase(Locale.ROOT));
	}

	private record AnnouncementKey(String houseManageNo, String pblancNo) {
	}

	private record TableSpec(String endpoint, String table, List<String> columns, List<String> keys) {
	}
}
