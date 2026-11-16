package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomePageCollector;
import com.toady.web.apt.api.ApplyhomeCompetitionApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.atomic.AtomicLong;

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
	private final JdbcTemplate jdbc;

	public ApplyhomeCompetitionSyncService(
			ApplyhomeApiClient announcementApiClient,
			ApplyhomeCompetitionApiClient competitionApiClient,
			ApplyhomeCompetitionPersistenceService persistenceService,
			ApplyhomeSyncLock syncLock,
			JdbcTemplate jdbc) {
		this.announcementApiClient = announcementApiClient;
		this.competitionApiClient = competitionApiClient;
		this.persistenceService = persistenceService;
		this.syncLock = syncLock;
		this.jdbc = jdbc;
	}

	/*
	 * 일정 기반 수집: 저장된 공고 일정을 보고 결과가 나올 때가 된 공고만 조회한다.
	 * - 경쟁률·특별공급 신청현황: 접수 기간이 범위와 겹치는 공고. 접수가 끝난 다음 날까지는 매번 다시 받고
	 *   (순위별 접수가 이어지며 값이 바뀐다), 그 뒤에는 아직 한 건도 없을 때만 받는다.
	 * - 당첨 가점: 당첨자 발표일이 범위 안이고 아직 한 건도 없는 공고. 한번 공개된 가점은 바뀌지 않는다.
	 * 범위 밖이거나 이미 받은 공고는 호출하지 않으므로, 자주 실행해도 공공데이터 호출량이 작다.
	 * 결과가 나올 날(접수일·접수 다음 날, 발표 당일)의 공고는 실행마다 받고, 그 뒤 비어 있는 공고(원래 결과가 없는
	 * 국민주택 가점·특별공급 없는 공고 등)는 COLD_RETRY 간격으로만 다시 확인한다(10분마다 같은 빈 공고를 부르지 않게).
	 * 저장은 기존 경쟁률 수집과 같은 공고 단위 교체라 여러 번 받아도 중복되지 않는다. 놓친 결과는 매일 도는
	 * 기존 경쟁률 수집(모집공고일 기준 최근 90일 전체)이 다시 채운다.
	 * 날짜 컬럼은 유형마다 'yyyy-MM-dd'와 'yyyyMMdd'가 섞여 있어 하이픈을 지우고 비교한다.
	 */
	private static final String APT_RECEIPT_BEGIN = "COALESCE(NULLIF(d.SPSPLY_RCEPT_BGNDE, ''), d.RCEPT_BGNDE)";
	static final List<ResultPhase> RESULT_PHASES = List.of(
			ResultPhase.rate(APT_RATE, "TB_APT_LTTOT_PBLANC_DETAIL", null, APT_RECEIPT_BEGIN, "d.RCEPT_ENDDE"),
			ResultPhase.rate(SPECIAL_SUPPLY_STATUS, "TB_APT_LTTOT_PBLANC_DETAIL", null, APT_RECEIPT_BEGIN, "d.RCEPT_ENDDE"),
			ResultPhase.score(APT_SCORE, "TB_APT_LTTOT_PBLANC_DETAIL"),
			ResultPhase.rate(CANCELLED_RATE, "TB_REMNDR_LTTOT_PBLANC_DETAIL", "06", "d.SUBSCRPT_RCEPT_BGNDE", "d.SUBSCRPT_RCEPT_ENDDE"),
			ResultPhase.rate(REMNANT_RATE, "TB_REMNDR_LTTOT_PBLANC_DETAIL", "04", "d.SUBSCRPT_RCEPT_BGNDE", "d.SUBSCRPT_RCEPT_ENDDE"),
			ResultPhase.rate(URBAN_RATE, "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", null, "d.SUBSCRPT_RCEPT_BGNDE", "d.SUBSCRPT_RCEPT_ENDDE"),
			ResultPhase.rate(PRIVATE_RENT_RATE, "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", null, "d.SUBSCRPT_RCEPT_BGNDE", "d.SUBSCRPT_RCEPT_ENDDE"),
			ResultPhase.rate(OPTIONAL_RATE, "TB_OPT_LTTOT_PBLANC_DETAIL", null, "d.SUBSCRPT_RCEPT_BGNDE", "d.SUBSCRPT_RCEPT_ENDDE"));

	/**
	 * 일정 기준 범위(접수일·당첨자 발표일)에 걸린 공고의 경쟁률·특별공급·가점만 갱신한다. 반환값은 조회한 공고×항목 수다.
	 * @param startDate 범위 시작(보통 오늘에서 재시도 일수를 뺀 날)
	 * @param endDate 범위 끝(보통 오늘)
	 */
	public ScheduledResult synchronizeScheduled(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("A valid start and end date range is required");
		}
		var result = new java.util.concurrent.atomic.AtomicReference<ScheduledResult>();
		List<String> failures = new ArrayList<>();
		syncLock.execute(() -> {
			int calls = 0;
			Set<AnnouncementKey> announcements = new LinkedHashSet<>();
			long now = System.currentTimeMillis();
			coldChecked.values().removeIf(at -> now - at > COLD_RETRY.toMillis());
			for (ResultPhase phase : RESULT_PHASES) {
				List<AnnouncementKey> due = new ArrayList<>();
				for (ScheduledTarget target : scheduledTargets(phase, startDate, endDate)) {
					// 비어 있는 공고의 재확인은 최근에 확인했으면 건너뛴다(값 대신 '확인 시각'만 이 JVM에 기억).
					if (!target.hot() && coldChecked.putIfAbsent(phase.table().table() + "|" + target.key(), now) != null) continue;
					due.add(target.key());
				}
				announcements.addAll(due);
				calls += due.size();
				syncForAnnouncements(phase.table(), due, failures);
			}
			result.set(new ScheduledResult(announcements.size(), calls));
		});
		if (!failures.isEmpty()) {
			throw new ApplyhomePartialFailureException("일정 기반 수집 중 " + failures.size() + "건 실패(나머지는 저장됨): "
					+ String.join(", ", failures.subList(0, Math.min(5, failures.size()))) + (failures.size() > 5 ? " 외" : ""));
		}
		return result.get();
	}

	/** 일정 기반 수집 결과: 대상 공고 수와 공고×항목 조회 수(대상이 없으면 둘 다 0, 외부 호출 없음). */
	public record ScheduledResult(int announcements, int calls) { }

	/** 비어 있는 공고를 다시 확인하는 간격. */
	static final java.time.Duration COLD_RETRY = java.time.Duration.ofHours(3);
	/** 비어 있는 공고(항목|공고)를 마지막으로 확인한 시각. 재시작하면 비지만 하루 몇 번 더 부를 뿐이다. */
	private final Map<String, Long> coldChecked = new java.util.concurrent.ConcurrentHashMap<>();

	/** hot: 결과가 나올 날이라 실행마다 받는다. 아니면 비어 있어서 다시 확인하는 공고다. */
	record ScheduledTarget(AnnouncementKey key, boolean hot) { }

	List<ScheduledTarget> scheduledTargets(ResultPhase phase, LocalDate startDate, LocalDate endDate) {
		String start = startDate.format(COMPACT_DATE);
		String end = endDate.format(COMPACT_DATE);
		String missing = "NOT EXISTS (SELECT 1 FROM " + phase.table().table()
				+ " r WHERE r.HOUSE_MANAGE_NO = d.HOUSE_MANAGE_NO AND r.PBLANC_NO = d.PBLANC_NO)";
		StringBuilder sql = new StringBuilder("SELECT DISTINCT d.HOUSE_MANAGE_NO, d.PBLANC_NO FROM " + phase.detailTable() + " d WHERE ");
		List<Object> args = new ArrayList<>();
		if (phase.houseSecd() != null) {
			sql.append("d.HOUSE_SECD = ? AND ");
			args.add(phase.houseSecd());
		}
		if (phase.score()) {
			// 발표 당일은 hot(10분마다), 지난 발표일은 비어 있을 때만 간격을 두고 다시 확인한다.
			sql.insert(sql.indexOf(" FROM "), ", CASE WHEN REPLACE(d.PRZWNER_PRESNATN_DE, '-', '') = ? THEN 1 ELSE 0 END");
			args.add(0, end);
			sql.append("REPLACE(d.PRZWNER_PRESNATN_DE, '-', '') BETWEEN ? AND ? AND ").append(missing);
			args.add(start);
			args.add(end);
		} else {
			// 접수 기간이 범위와 겹치고, 접수가 끝난 다음 날까지는 매번 다시 받는다. 그 뒤로는 비어 있을 때만 받는다.
			String begin = "REPLACE(" + phase.beginColumn() + ", '-', '')";
			String finish = "REPLACE(" + phase.endColumn() + ", '-', '')";
			// 접수 중이거나 접수 다음 날이면 hot.
			sql.insert(sql.indexOf(" FROM "), ", CASE WHEN " + finish + " >= ? THEN 1 ELSE 0 END");
			args.add(0, endDate.minusDays(1).format(COMPACT_DATE));
			sql.append(begin).append(" <= ? AND ").append(finish).append(" >= ? AND (")
					.append(finish).append(" >= ? OR ").append(missing).append(")");
			args.add(end);
			args.add(start);
			args.add(endDate.minusDays(1).format(COMPACT_DATE));
		}
		sql.append(" ORDER BY d.HOUSE_MANAGE_NO, d.PBLANC_NO");
		return jdbc.query(sql.toString(), (rs, n) -> new ScheduledTarget(new AnnouncementKey(rs.getString(1), rs.getString(2)), rs.getInt(3) == 1), args.toArray());
	}

	record ResultPhase(TableSpec table, String detailTable, String houseSecd, String beginColumn, String endColumn, boolean score) {
		static ResultPhase rate(TableSpec table, String detailTable, String houseSecd, String begin, String end) {
			return new ResultPhase(table, detailTable, houseSecd, begin, end, false);
		}
		static ResultPhase score(TableSpec table, String detailTable) {
			return new ResultPhase(table, detailTable, null, null, null, true);
		}
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
		var count = new AtomicLong();
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
				if (failure instanceof ApplyhomeApiClient.ProviderRejectedException) throw failure;
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

	record TableSpec(String endpoint, String table, List<String> columns, List<String> keys) {
	}
}
