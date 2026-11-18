package com.toady.web.app.planner.research;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 국토부 실거래 API의 하루 호출 수(한국시간 자정 기준)를 서버 전체로 센다.
 * 사용자 요청(지도·동네 실거래)은 하루 한도의 USER_SHARE까지만 쓰고, 남은 몫은 저녁 미리 계산(주변 시세 비교)에 남긴다.
 * 시·군·구를 바꿔 가며 부르는 요청이 몰려도 하루 한도가 바닥나 자정까지 실거래 기능 전체가 멈추지 않게 한다.
 * limit가 0 이하면 세지 않는다(테스트·키 없는 환경).
 */
public final class MolitDailyBudget {
    static final double USER_SHARE = 0.8;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final long limit;
    private final Clock clock;
    private LocalDate day;
    private long used;
    /** 관리자 설정 한도와 DB 계수기(database.sql 27번). 연결되면 재시작해도 이어서 센다. 없으면 메모리로 센다. */
    private volatile com.toady.web.app.operation.AppSettings settings;
    private volatile com.toady.web.app.operation.DailyCounter counter;
    static final String COUNTER_KEY = "molit";

    /** 스프링 설정에서 관리자 설정·DB 계수기를 연결한다. 연결 전에는 메모리 계수만 쓴다. */
    public void connect(com.toady.web.app.operation.AppSettings settings, com.toady.web.app.operation.DailyCounter counter) {
        this.settings = settings; this.counter = counter;
    }
    // 관리자 화면 값(100~1,000,000)이 있으면 그 값을, 없으면 생성자에서 받은 기본 한도를 쓴다.
    long limit() { return settings == null || limit <= 0 ? limit : settings.number(com.toady.web.app.operation.AppSettings.MOLIT_DAILY_LIMIT, limit, 100, 1_000_000); }

    public MolitDailyBudget(long limit, Clock clock) { this.limit = limit; this.clock = clock; }

    public static MolitDailyBudget unlimited() { return new MolitDailyBudget(0, Clock.system(KST)); }

    /** 한 번 부를 수 있으면 세고 true. background는 미리 계산 작업(남겨 둔 몫까지 쓸 수 있음). */
    // synchronized: 여러 요청 스레드가 동시에 세어도 숫자가 꼬이지 않게 한 번에 하나씩 처리한다.
    // DB 계수기가 있으면 그쪽 결과(null이면 DB 실패)를 따르고, 없거나 실패하면 메모리로 센다.
    public synchronized boolean tryAcquire(boolean background) {
        long limit = limit();
        if (limit <= 0) return true;
        LocalDate today = LocalDate.now(clock.withZone(KST));
        if (!today.equals(day)) { day = today; used = 0; }
        long cap = background ? limit : (long) Math.floor(limit * USER_SHARE);
        if (counter != null) {
            Boolean shared = counter.tryIncrement(COUNTER_KEY, today, cap);
            if (shared != null) { if (shared) used++; return shared; }
        }
        if (used >= cap) return false;
        used++;
        return true;
    }

    /** 오늘 쓴 호출 수(관리자 화면 표시용). */
    public synchronized long used() {
        if (counter != null && limit > 0) {
            Long shared = counter.count(COUNTER_KEY, LocalDate.now(clock.withZone(KST)));
            if (shared != null) return shared;
        }
        return day != null && day.equals(LocalDate.now(clock.withZone(KST))) ? used : 0;
    }
}
