package com.toady.web.app.announcement;

import com.toady.web.app.announcement.dto.AnnouncementListItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 공고 한 건을 '실제로 신청할 수 있는 날'로 바꾸는 규칙. '오늘 접수' 숫자(weekly-summary의 applyToday)가 이 규칙을 쓰고,
 * 웹 달력은 같은 규칙을 toadyReactWeb/src/features/announcement/applyDays.js로 쓴다. 규칙을 바꾸면 두 곳을 함께 고친다.
 *
 * - APT: 특별공급 접수 기간(spsplyRceptBgnde~spsplyRceptEndde) + 1·2순위 지역별(해당·경기·기타) 접수일(Rcptde~Endde).
 *   특별공급 날짜가 없으면 청약접수 시작일(rceptBgnde)이 첫 1순위 접수일보다 앞설 때 그날을 특별공급일로 본다.
 *   순위 날짜가 하나도 없으면(특별공급만 있는 경우 포함) 청약접수 기간(rceptBgnde~rceptEndde) 전체를 신청일로 본다.
 * - 그 밖의 유형(무순위·임의공급·오피스텔·민간임대 등): 접수 시작일~종료일의 모든 날.
 *   유형마다 원래 필드 이름이 다르지만 AnnouncementListMapper가 rceptBgnde/rceptEndde로 맞춰 둔다.
 * 주말도 날짜 그대로 판정한다(달력이 주말 칸을 그리지 않을 뿐이다).
 */
public final class ApplyDays {
    /**
     * 한 접수 기간을 최대 며칠까지 신청일로 펼칠지. 종료일이 99991231처럼 잘못 들어와도 끝없이 펼치지 않는다.
     * 기간이 겹치는 공고를 찾을 때(AnnouncementSearchService overlap, 이번 주 청약 집계)도 시작일을 이만큼만 거슬러 올라간다.
     * 웹 applyDays.js의 MAX_PERIOD_DAYS와 같은 값이어야 한다(달력에 그려지는 범위와 조회 범위가 같게).
     */
    public static final int MAX_PERIOD_DAYS = 400;

    private ApplyDays() {
    }

    /** 신청 가능한 날짜(오름차순, 중복 없음). 날짜를 하나도 알 수 없으면 빈 목록. */
    public static List<LocalDate> of(AnnouncementListItem item) {
        TreeSet<LocalDate> days = new TreeSet<>();
        LocalDate start = date(item.rceptBgnde()), end = date(item.rceptEndde());
        if (!"apt".equals(item.type())) {
            addPeriod(days, start, end);
            return List.copyOf(days);
        }
        TreeSet<LocalDate> rank1 = new TreeSet<>(), ranks = new TreeSet<>();
        addPeriod(rank1, date(item.gnrlRnk1CrspareaRcptde()), date(item.gnrlRnk1CrspareaEndde()));
        addPeriod(rank1, date(item.gnrlRnk1EtcGgRcptde()), date(item.gnrlRnk1EtcGgEndde()));
        addPeriod(rank1, date(item.gnrlRnk1EtcAreaRcptde()), date(item.gnrlRnk1EtcAreaEndde()));
        ranks.addAll(rank1);
        addPeriod(ranks, date(item.gnrlRnk2CrspareaRcptde()), date(item.gnrlRnk2CrspareaEndde()));
        addPeriod(ranks, date(item.gnrlRnk2EtcGgRcptde()), date(item.gnrlRnk2EtcGgEndde()));
        addPeriod(ranks, date(item.gnrlRnk2EtcAreaRcptde()), date(item.gnrlRnk2EtcAreaEndde()));
        if (ranks.isEmpty()) {
            addPeriod(days, start, end);
            return List.copyOf(days);
        }
        LocalDate special = date(item.spsplyRceptBgnde());
        if (special != null) addPeriod(days, special, date(item.spsplyRceptEndde()));
        else if (start != null && (rank1.isEmpty() || start.isBefore(rank1.first()))) days.add(start);
        days.addAll(ranks);
        return List.copyOf(days);
    }

    /** 그날 신청할 수 있는지. */
    public static boolean on(AnnouncementListItem item, LocalDate day) {
        return day != null && of(item).contains(day);
    }

    /** 오늘을 포함해 가장 가까운 신청일. 남은 신청일이 없으면 null. */
    public static LocalDate next(AnnouncementListItem item, LocalDate today) {
        for (LocalDate day : of(item)) if (!day.isBefore(today)) return day;
        return null;
    }

    /** 시작일~종료일(종료일이 없거나 앞서면 시작일 하루)을 모두 넣는다. */
    private static void addPeriod(TreeSet<LocalDate> days, LocalDate start, LocalDate end) {
        if (start == null) return;
        LocalDate last = end == null || end.isBefore(start) ? start : end;
        if (last.isAfter(start.plusDays(MAX_PERIOD_DAYS))) last = start.plusDays(MAX_PERIOD_DAYS);
        for (LocalDate day = start; !day.isAfter(last); day = day.plusDays(1)) days.add(day);
    }

    /** '2026-11-17', '20261117', '2026.11.17 09:00' 등에서 앞 8자리 숫자로 날짜를 읽는다. 날짜가 아니면 null. */
    static LocalDate date(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 8) return null;
        try {
            return LocalDate.of(Integer.parseInt(digits.substring(0, 4)), Integer.parseInt(digits.substring(4, 6)), Integer.parseInt(digits.substring(6, 8)));
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /** 테스트·로그용: 날짜 목록을 'YYYY-MM-DD' 문자열로. */
    static List<String> strings(AnnouncementListItem item) {
        List<String> result = new ArrayList<>();
        of(item).forEach(day -> result.add(day.toString()));
        return result;
    }
}
