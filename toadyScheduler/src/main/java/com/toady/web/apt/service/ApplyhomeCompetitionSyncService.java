package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomeApiPage;
import com.toady.web.apt.api.ApplyhomeCompetitionApiClient;
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

/** 공고별 경쟁률, 당첨가점, 특별공급 신청현황을 수집한다. */
@Service
public class ApplyhomeCompetitionSyncService {

	private static final Logger log = LoggerFactory.getLogger(ApplyhomeCompetitionSyncService.class);
	private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	private static final List<String> ANNOUNCEMENT_KEYS = List.of("HOUSE_MANAGE_NO", "PBLANC_NO");

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

	public void synchronizeCurrentAnnouncements() {
		LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
		synchronize(today.minusMonths(1), today.plusMonths(1));
	}

	public void synchronize(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("A valid start and end date range is required");
		}
		syncLock.execute(() -> synchronizeLocked(startDate, endDate));
	}

	private void synchronizeLocked(LocalDate startDate, LocalDate endDate) {
		List<AnnouncementKey> aptAnnouncements = discover("getAPTLttotPblancDetail", startDate, endDate, false);
		List<AnnouncementKey> urbanAnnouncements = discover(
				"getUrbtyOfctlLttotPblancDetail", startDate, endDate, false);
		List<AnnouncementKey> remnantAnnouncements = discover(
				"getRemndrLttotPblancDetail", startDate, endDate, false);
		List<AnnouncementKey> privateRentAnnouncements = discover(
				"getPblPvtRentLttotPblancDetail", startDate, endDate, true);
		List<AnnouncementKey> optionalAnnouncements = discover("getOPTLttotPblancDetail", startDate, endDate, true);

		syncForAnnouncements(APT_RATE, aptAnnouncements);
		syncForAnnouncements(CANCELLED_RATE, aptAnnouncements);
		syncForAnnouncements(APT_SCORE, aptAnnouncements);
		syncForAnnouncements(SPECIAL_SUPPLY_STATUS, aptAnnouncements);
		syncForAnnouncements(URBAN_RATE, urbanAnnouncements);
		syncForAnnouncements(REMNANT_RATE, remnantAnnouncements);
		syncForAnnouncements(PRIVATE_RENT_RATE, privateRentAnnouncements);
		syncForAnnouncements(OPTIONAL_RATE, optionalAnnouncements);
	}

	private List<AnnouncementKey> discover(
			String endpoint, LocalDate startDate, LocalDate endDate, boolean compactDate) {
		DateTimeFormatter formatter = compactDate ? COMPACT_DATE : DateTimeFormatter.ISO_LOCAL_DATE;
		Map<String, String> filters = Map.of(
				"cond[RCRIT_PBLANC_DE::GTE]", startDate.format(formatter),
				"cond[RCRIT_PBLANC_DE::LTE]", endDate.format(formatter));
		List<JsonNode> rows = fetchAllPages(announcementApiClient, endpoint, filters);
		Set<AnnouncementKey> keys = new LinkedHashSet<>();
		for (JsonNode row : rows) {
			JsonNode houseManageNo = row.get("HOUSE_MANAGE_NO");
			JsonNode pblancNo = row.get("PBLANC_NO");
			if (houseManageNo == null || houseManageNo.isNull() || pblancNo == null || pblancNo.isNull()) {
				throw new IllegalStateException("Applyhome announcement response is missing keys: " + endpoint);
			}
			keys.add(new AnnouncementKey(houseManageNo.asText(), pblancNo.asText()));
		}
		log.info("Discovered Applyhome announcements endpoint={} count={}", endpoint, keys.size());
		return List.copyOf(keys);
	}

	private void syncForAnnouncements(TableSpec table, List<AnnouncementKey> announcements) {
		long totalRows = 0;
		for (AnnouncementKey key : announcements) {
			Map<String, String> filters = Map.of(
					"cond[HOUSE_MANAGE_NO::EQ]", key.houseManageNo(),
					"cond[PBLANC_NO::EQ]", key.pblancNo());
			List<JsonNode> rows = fetchAllPages(competitionApiClient, table.endpoint(), filters);
			persistenceService.upsert(table.table(), table.columns(), table.keys(), rows);
			totalRows += rows.size();
		}
		log.info("Synchronized Applyhome endpoint={} announcements={} rows={}",
				table.endpoint(), announcements.size(), totalRows);
	}

	private List<JsonNode> fetchAllPages(ApplyhomeApiClient client, String endpoint, Map<String, String> filters) {
		List<JsonNode> rows = new ArrayList<>();
		int page = 1;
		long matchCount;
		do {
			ApplyhomeApiPage response = client.getPage(endpoint, page, filters);
			matchCount = response.matchCount();
			rows.addAll(response.data());
			if (rows.size() >= matchCount) {
				break;
			}
			if (response.currentCount() == 0) {
				throw new IllegalStateException("Applyhome API returned fewer rows than matchCount: " + endpoint);
			}
			page++;
		} while (true);
		return rows;
	}

	private List<JsonNode> fetchAllPages(
			ApplyhomeCompetitionApiClient client, String endpoint, Map<String, String> filters) {
		List<JsonNode> rows = new ArrayList<>();
		int page = 1;
		long matchCount;
		do {
			ApplyhomeApiPage response = client.getPage(endpoint, page, filters);
			matchCount = response.matchCount();
			rows.addAll(response.data());
			if (rows.size() >= matchCount) {
				break;
			}
			if (response.currentCount() == 0) {
				throw new IllegalStateException("Applyhome competition API returned fewer rows than matchCount: "
						+ endpoint);
			}
			page++;
		} while (true);
		return rows;
	}

	private record AnnouncementKey(String houseManageNo, String pblancNo) {
	}

	private record TableSpec(String endpoint, String table, List<String> columns, List<String> keys) {
	}
}
