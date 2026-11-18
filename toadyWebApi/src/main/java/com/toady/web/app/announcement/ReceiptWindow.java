package com.toady.web.app.announcement;

import java.time.LocalDate;

/**
 * '접수 중'·'접수 예정' 공고를 고르는 공통 범위. 지역별 청약(/regions, 앱 active-announcements), 생활권·줍줍 공개 페이지(/areas, /zupzup),
 * 자격 판정 목록(EligibilityFeed), 이번 주 청약, 웹 푸시가 모두 이 값으로 세어 화면마다 건수가 달라지지 않게 한다.
 *
 * - 접수 중: 접수 시작일 ≤ 오늘 ≤ 마감일. 단 접수 기간은 시작일부터 ApplyDays.MAX_PERIOD_DAYS(400일)까지만 인정한다
 *   (AnnouncementSearchService의 overlap 조건, 달력이 신청일을 펼치는 범위와 같다). 마감일이 99991231처럼 잘못 들어온 공고가 끝없이 '접수 중'으로 남지 않는다.
 * - 접수 예정: 오늘 < 접수 시작일 ≤ 오늘 + UPCOMING_DAYS.
 */
public final class ReceiptWindow {
    /**
     * 접수 예정으로 보여 줄 최대 일수. 청약 공고는 보통 접수 2주 전쯤 올라오지만 공공지원 민간임대 등은 몇 달 앞서 올라오기도 한다.
     * 데이터에는 이미 공고된 것만 있으므로 넉넉히 잡아도 잡음이 늘지 않아, 기존에 가장 넓던 생활권·줍줍 페이지 값(180일)으로 맞췄다
     * (60일로 맞추면 그 페이지들에서 보이던 공고가 빠진다).
     */
    public static final int UPCOMING_DAYS = 180;
    /** 접수 중으로 인정하는 최대 기간(시작일부터). ApplyDays.MAX_PERIOD_DAYS와 같다. */
    public static final int MAX_OPEN_DAYS = ApplyDays.MAX_PERIOD_DAYS;

    private ReceiptWindow() {
    }

    /** 실제로 인정하는 마지막 접수일: 마감일(없거나 시작일보다 앞서면 시작일)과 시작일+400일 중 이른 날. start가 null이면 null. */
    public static LocalDate lastDay(LocalDate start, LocalDate end) {
        if (start == null) return null;
        LocalDate last = end == null || end.isBefore(start) ? start : end;
        LocalDate cap = start.plusDays(MAX_OPEN_DAYS);
        return last.isAfter(cap) ? cap : last;
    }

    /** 오늘 접수 중인지: 시작일 ≤ 오늘 ≤ 인정 마지막 접수일. */
    public static boolean open(LocalDate start, LocalDate end, LocalDate today) {
        return start != null && !start.isAfter(today) && !lastDay(start, end).isBefore(today);
    }

    /** 접수 예정 범위 안인지: 오늘 < 시작일 ≤ 오늘 + UPCOMING_DAYS. */
    public static boolean upcoming(LocalDate start, LocalDate today) {
        return start != null && start.isAfter(today) && !start.isAfter(today.plusDays(UPCOMING_DAYS));
    }

    /** 접수 중 또는 예정 범위 안. */
    public static boolean active(LocalDate start, LocalDate end, LocalDate today) {
        return open(start, end, today) || upcoming(start, today);
    }

    /** 접수 중·예정 공고를 찾는 조회 범위의 끝(접수 시작일 기준). 조회 시작은 overlap 검색이 오늘-400일로 맞춘다. */
    public static LocalDate searchEnd(LocalDate today) { return today.plusDays(UPCOMING_DAYS); }
}
