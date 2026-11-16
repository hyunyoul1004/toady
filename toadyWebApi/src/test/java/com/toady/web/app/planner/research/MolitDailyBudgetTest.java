package com.toady.web.app.planner.research;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 국토부 하루 호출 몫: 사용자 조회는 80%까지, 미리 계산은 남은 몫까지, 한국시간 자정에 다시 센다. */
class MolitDailyBudgetTest {
    @Test void usersStopAtEightyPercentAndBackgroundCanUseTheRest() {
        MolitDailyBudget budget = new MolitDailyBudget(10, Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < 8; i++) assertTrue(budget.tryAcquire(false));
        assertFalse(budget.tryAcquire(false), "사용자 조회는 10회 중 8회까지");
        assertTrue(budget.tryAcquire(true)); assertTrue(budget.tryAcquire(true));
        assertFalse(budget.tryAcquire(true), "미리 계산도 하루 한도를 넘지 않는다");
        assertEquals(10, budget.used());
    }

    @Test void theCountResetsAtMidnightKoreaTime() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-11-16T14:59:00Z")); // 한국시간 23:59
        Clock clock = new Clock() {
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return Clock.fixed(now.get(), zone); }
            public Instant instant() { return now.get(); }
        };
        MolitDailyBudget budget = new MolitDailyBudget(1, clock);
        assertFalse(budget.tryAcquire(false), "한도 1이면 사용자 몫은 0회");
        assertTrue(budget.tryAcquire(true));
        now.set(Instant.parse("2026-11-16T15:00:00Z")); // 한국시간 다음 날 00:00
        assertTrue(budget.tryAcquire(true), "자정에 다시 센다");
    }

    @Test void theServiceStopsCallingTheProviderWhenTheUserShareIsUsed() {
        AtomicInteger calls = new AtomicInteger();
        var service = new NeighborhoodResearchService((uri, headers) -> { calls.incrementAndGet();
            return "<response><header><resultCode>000</resultCode></header><body><items></items><totalCount>0</totalCount></body></response>".getBytes(); },
            "key", new MolitDailyBudget(5, Clock.system(ZoneId.of("Asia/Seoul"))));
        // 사용자 조회: 5회의 80% = 4회까지만 실제로 부른다(시·군·구마다 3개월 조회라 두 번째 지역의 둘째 달에서 멈춘다).
        service.trades("1168010100", "역삼동");   // 강남구 3회
        service.trades("1165010100", "반포동");   // 서초구 1회 뒤 몫이 끝남
        assertEquals(4, calls.get(), "사용자 몫(4회)을 넘겨 부르지 않는다");
        assertEquals(4, service.molitCallsToday());
        // 미리 계산은 남은 1회를 쓸 수 있다.
        service.inBackground(() -> service.trades("1171010100", "잠실동"));   // 송파구
        assertEquals(5, calls.get());
    }
}
