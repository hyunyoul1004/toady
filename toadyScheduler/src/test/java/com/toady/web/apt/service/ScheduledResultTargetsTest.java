package com.toady.web.apt.service;

import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomeApiPage;
import com.toady.web.apt.api.ApplyhomeCompetitionApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 일정 맞춤 수집이 접수일·당첨자 발표일과 이미 받은 결과를 보고 필요한 공고만 고르는지 검사한다. */
@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:scheduledTargets;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduledResultTargetsTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 17);
    static final LocalDate START = TODAY.minusDays(7);

    @Autowired JdbcTemplate jdbc;
    ApplyhomeCompetitionApiClient competitionApi = mock(ApplyhomeCompetitionApiClient.class);
    ApplyhomeCompetitionPersistenceService persistence = mock(ApplyhomeCompetitionPersistenceService.class);
    ApplyhomeSyncLock lock = mock(ApplyhomeSyncLock.class);
    ApplyhomeCompetitionSyncService service;

    @BeforeEach
    void setUp() {
        for (String table : List.of("TB_APT_LTTOT_PBLANC_DETAIL", "TB_REMNDR_LTTOT_PBLANC_DETAIL", "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL",
                "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_DETAIL", "TB_APT_LTTOT_PBLANC_CMPET", "TB_APT_SPSPLY_REQST_STUS",
                "TB_APT_LTTOT_PBLANC_SCORE", "TB_CANC_RESPL_LTTOT_PBLANC_CMPET", "TB_REMNDR_LTTOT_PBLANC_CMPET",
                "TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET", "TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET", "TB_OPT_LTTOT_PBLANC_CMPET")) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        jdbc.execute("CREATE TABLE TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), SPSPLY_RCEPT_BGNDE VARCHAR(10),"
                + " RCEPT_BGNDE VARCHAR(10), RCEPT_ENDDE VARCHAR(10), PRZWNER_PRESNATN_DE VARCHAR(10))");
        jdbc.execute("CREATE TABLE TB_REMNDR_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), HOUSE_SECD VARCHAR(2),"
                + " SUBSCRPT_RCEPT_BGNDE VARCHAR(10), SUBSCRPT_RCEPT_ENDDE VARCHAR(10))");
        for (String table : List.of("TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_DETAIL")) {
            jdbc.execute("CREATE TABLE " + table + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), SUBSCRPT_RCEPT_BGNDE VARCHAR(10), SUBSCRPT_RCEPT_ENDDE VARCHAR(10))");
        }
        for (String table : List.of("TB_APT_LTTOT_PBLANC_CMPET", "TB_APT_SPSPLY_REQST_STUS", "TB_APT_LTTOT_PBLANC_SCORE", "TB_CANC_RESPL_LTTOT_PBLANC_CMPET",
                "TB_REMNDR_LTTOT_PBLANC_CMPET", "TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET", "TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET", "TB_OPT_LTTOT_PBLANC_CMPET")) {
            jdbc.execute("CREATE TABLE " + table + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40))");
        }
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; }).when(lock).execute(any(Runnable.class));
        service = new ApplyhomeCompetitionSyncService(mock(ApplyhomeApiClient.class), competitionApi, persistence, lock, jdbc);
    }

    void apt(String id, String special, String begin, String end, String winners) {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES (?, ?, ?, ?, ?, ?)", id, id, special, begin, end, winners);
    }
    void saved(String table, String id) { jdbc.update("INSERT INTO " + table + " VALUES (?, ?)", id, id); }
    List<String> targets(String endpoint) {
        var phase = phase(endpoint);
        return service.scheduledTargets(phase, START, TODAY).stream().map(target -> target.key().houseManageNo()).toList();
    }
    List<String> hot(String table) {
        return service.scheduledTargets(phase(table), START, TODAY).stream().filter(ApplyhomeCompetitionSyncService.ScheduledTarget::hot)
                .map(target -> target.key().houseManageNo()).toList();
    }
    static ApplyhomeCompetitionSyncService.ResultPhase phase(String table) {
        return ApplyhomeCompetitionSyncService.RESULT_PHASES.stream().filter(p -> p.table().table().equals(table)).findFirst().orElseThrow();
    }

    @Test
    void ratesAreRefreshedDuringReceiptAndOnlyRefilledWhenMissingAfterwards() {
        apt("IN_PROGRESS", null, "2026-11-16", "2026-11-18", "2026-11-25");
        saved("TB_APT_LTTOT_PBLANC_CMPET", "IN_PROGRESS");
        apt("SPECIAL_TODAY", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-26");
        apt("ENDED_YESTERDAY", null, "2026-11-15", "2026-11-16", "2026-11-24");
        saved("TB_APT_LTTOT_PBLANC_CMPET", "ENDED_YESTERDAY");
        apt("ENDED_SAVED", null, "2026-11-12", "2026-11-14", "2026-11-21");
        saved("TB_APT_LTTOT_PBLANC_CMPET", "ENDED_SAVED");
        apt("ENDED_MISSING", null, "2026-11-12", "2026-11-14", "2026-11-21");
        apt("TOO_OLD", null, "2026-11-01", "2026-11-03", "2026-11-10");
        apt("FUTURE", null, "2026-11-20", "2026-11-22", "2026-11-29");

        // 접수 중·접수 다음 날까지는 이미 받았어도 다시 받고(순위별로 값이 바뀜), 그 뒤엔 빈 공고만 다시 받는다.
        assertThat(targets("TB_APT_LTTOT_PBLANC_CMPET"))
                .containsExactly("ENDED_MISSING", "ENDED_YESTERDAY", "IN_PROGRESS", "SPECIAL_TODAY");
        assertThat(targets("TB_APT_SPSPLY_REQST_STUS"))
                .containsExactly("ENDED_MISSING", "ENDED_SAVED", "ENDED_YESTERDAY", "IN_PROGRESS", "SPECIAL_TODAY");
    }

    @Test
    void scoresAreFetchedFromAnnouncementDayUntilTheyArrive() {
        apt("TODAY", null, "2026-11-05", "2026-11-07", "2026-11-17");
        apt("TODAY_SAVED", null, "2026-11-05", "2026-11-07", "2026-11-17");
        saved("TB_APT_LTTOT_PBLANC_SCORE", "TODAY_SAVED");
        apt("LATE", null, "2026-11-01", "2026-11-03", "2026-11-12");
        apt("NOT_YET", null, "2026-11-16", "2026-11-18", "2026-11-25");
        apt("EXPIRED", null, "2026-10-25", "2026-10-27", "2026-11-03");

        assertThat(targets("TB_APT_LTTOT_PBLANC_SCORE")).containsExactly("LATE", "TODAY");
    }

    @Test
    void otherTypesUseTheirOwnColumnsHouseTypeAndCompactDates() {
        jdbc.update("INSERT INTO TB_REMNDR_LTTOT_PBLANC_DETAIL VALUES ('REMNANT','REMNANT','04','2026-11-17','2026-11-17')");
        jdbc.update("INSERT INTO TB_REMNDR_LTTOT_PBLANC_DETAIL VALUES ('CANCELLED','CANCELLED','06','2026-11-16','2026-11-16')");
        jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL VALUES ('OPTIONAL','OPTIONAL','20261116','20261118')");
        jdbc.update("INSERT INTO TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL VALUES ('URBAN','URBAN','2026-11-01','2026-11-02')");

        assertThat(targets("TB_REMNDR_LTTOT_PBLANC_CMPET")).containsExactly("REMNANT");
        assertThat(targets("TB_CANC_RESPL_LTTOT_PBLANC_CMPET")).containsExactly("CANCELLED");
        assertThat(targets("TB_OPT_LTTOT_PBLANC_CMPET")).containsExactly("OPTIONAL");
        assertThat(targets("TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET")).isEmpty();
    }

    @Test
    void onlyResultDaysAreHotAndEmptyLeftoversAreRecheckedSparingly() {
        apt("IN_PROGRESS", null, "2026-11-16", "2026-11-18", "2026-11-25");
        apt("ENDED_YESTERDAY", null, "2026-11-15", "2026-11-16", "2026-11-24");
        apt("ENDED_MISSING", null, "2026-11-12", "2026-11-14", "2026-11-21");
        apt("WINNERS_TODAY", null, "2026-11-05", "2026-11-07", "2026-11-17");
        apt("WINNERS_LAST_WEEK", null, "2026-11-01", "2026-11-03", "2026-11-12");
        assertThat(hot("TB_APT_LTTOT_PBLANC_CMPET")).containsExactly("ENDED_YESTERDAY", "IN_PROGRESS");
        assertThat(hot("TB_APT_LTTOT_PBLANC_SCORE")).containsExactly("WINNERS_TODAY");

        when(competitionApi.getPage(anyString(), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        var first = service.synchronizeScheduled(START, TODAY);
        // 두 번째 실행(10분 뒤)은 결과가 나올 날의 공고만 다시 받고, 비어 있는 지난 공고는 간격을 둔다.
        var second = service.synchronizeScheduled(START, TODAY);
        assertThat(second.calls()).isLessThan(first.calls());
        verify(competitionApi, times(2)).getPage(eq("getAptLttotPblancScore"), eq(1),
                eq(Map.of("cond[HOUSE_MANAGE_NO::EQ]", "WINNERS_TODAY", "cond[PBLANC_NO::EQ]", "WINNERS_TODAY")));
        verify(competitionApi, times(1)).getPage(eq("getAptLttotPblancScore"), eq(1),
                eq(Map.of("cond[HOUSE_MANAGE_NO::EQ]", "WINNERS_LAST_WEEK", "cond[PBLANC_NO::EQ]", "WINNERS_LAST_WEEK")));
        verify(competitionApi, times(1)).getPage(eq("getAPTLttotPblancCmpet"), eq(1),
                eq(Map.of("cond[HOUSE_MANAGE_NO::EQ]", "ENDED_MISSING", "cond[PBLANC_NO::EQ]", "ENDED_MISSING")));
        verify(competitionApi, times(2)).getPage(eq("getAPTLttotPblancCmpet"), eq(1),
                eq(Map.of("cond[HOUSE_MANAGE_NO::EQ]", "IN_PROGRESS", "cond[PBLANC_NO::EQ]", "IN_PROGRESS")));
    }

    @Test
    void aFailedEmptyLeftoverIsRetriedOnTheNextRun() {
        apt("WINNERS_LAST_WEEK", null, "2026-11-01", "2026-11-03", "2026-11-12");
        var filters = Map.of("cond[HOUSE_MANAGE_NO::EQ]", "WINNERS_LAST_WEEK", "cond[PBLANC_NO::EQ]", "WINNERS_LAST_WEEK");
        when(competitionApi.getPage(anyString(), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        // 첫 실행은 일시적인 오류로 실패한다. 확인 시각을 남기지 않아야 다음 실행(10분 뒤)에서 바로 다시 받는다.
        when(competitionApi.getPage(eq("getAptLttotPblancScore"), eq(1), eq(filters)))
                .thenThrow(new IllegalStateException("timeout"))
                .thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.synchronizeScheduled(START, TODAY))
                .isInstanceOf(ApplyhomePartialFailureException.class);
        service.synchronizeScheduled(START, TODAY);
        // 성공한 뒤에는 재확인 간격(3시간)이 적용되어 세 번째 실행에서는 부르지 않는다.
        service.synchronizeScheduled(START, TODAY);
        verify(competitionApi, times(2)).getPage(eq("getAptLttotPblancScore"), eq(1), eq(filters));
    }

    @Test
    void aPersistentlyFailingAnnouncementBacksOffWhileOthersKeepRunning() {
        apt("IN_PROGRESS", null, "2026-11-16", "2026-11-18", "2026-11-25");
        var filters = Map.of("cond[HOUSE_MANAGE_NO::EQ]", "IN_PROGRESS", "cond[PBLANC_NO::EQ]", "IN_PROGRESS");
        when(competitionApi.getPage(anyString(), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        when(competitionApi.getPage(eq("getAPTLttotPblancCmpet"), eq(1), eq(filters))).thenThrow(new IllegalStateException("broken"));
        long[] now = {0};
        service.clock = () -> now[0];
        long minute = 60_000;
        // 10분마다 실행: 첫 실패는 바로 다음 실행에서 다시 받고, 두 번 연속 실패하면 30분, 그다음 1시간을 기다린다.
        int runs = 0;
        for (; now[0] <= 120 * minute; now[0] += 10 * minute, runs++) {
            try { service.synchronizeScheduled(START, TODAY); } catch (ApplyhomePartialFailureException ignored) { }
        }
        // 0분(1회), 10분(2회) → 30분 대기 → 40분(3회) → 1시간 대기 → 100분(4회). 13번 실행 중 4번만 부른다.
        verify(competitionApi, times(4)).getPage(eq("getAPTLttotPblancCmpet"), eq(1), eq(filters));
        // 같은 공고의 특별공급 신청현황(다른 항목)은 실패하지 않으므로 실행마다 받는다.
        verify(competitionApi, times(runs)).getPage(eq("getAPTSpsplyReqstStus"), eq(1), eq(filters));
    }

    @Test
    void synchronizeCallsOnlyTargetsAndSkipsTheApiWhenNothingIsDue() {
        assertThat(service.synchronizeScheduled(START, TODAY)).isEqualTo(new ApplyhomeCompetitionSyncService.ScheduledResult(0, 0));
        verifyNoInteractions(competitionApi);

        apt("IN_PROGRESS", null, "2026-11-16", "2026-11-18", "2026-11-25");
        when(competitionApi.getPage(anyString(), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        // 경쟁률 + 특별공급 신청현황 두 항목만 조회한다(가점은 발표일 전이라 제외).
        assertThat(service.synchronizeScheduled(START, TODAY)).isEqualTo(new ApplyhomeCompetitionSyncService.ScheduledResult(1, 2));
        verify(competitionApi).getPage(eq("getAPTLttotPblancCmpet"), eq(1),
                eq(Map.of("cond[HOUSE_MANAGE_NO::EQ]", "IN_PROGRESS", "cond[PBLANC_NO::EQ]", "IN_PROGRESS")));
        verify(competitionApi).getPage(eq("getAPTSpsplyReqstStus"), eq(1), anyMap());
        verifyNoMoreInteractions(competitionApi);
    }
}
