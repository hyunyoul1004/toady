package com.toady.web.app.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.ReceiptChannel;
import com.toady.web.app.announcement.service.AnnouncementService;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 관심 공고 키(type/house/notice)로 원본 공고를 읽어 화면·메일·캘린더에 쓸 요약(Summary)을 만든다.
 * AnnouncementService.getDetail()이 돌려주는 청약홈 원본 필드(rceptBgnde 등)를 이 안에서만 해석한다.
 * 클라이언트의 제목/날짜를 신뢰하지 않는다. PlannerService, LivingAreas, AreaCalendar, 공개 페이지들이 쓴다.
 */
// @Service: 스프링 빈으로 등록한다.
@Service
public class PlannerSummary {
    private final AnnouncementService announcements;
    private final ObjectMapper mapper;
    public PlannerSummary(AnnouncementService announcements, ObjectMapper mapper) { this.announcements = announcements; this.mapper = mapper; }
    /** 일정 하나(라벨 + 날짜). */
    public record Event(String label, LocalDate date) { }
    /**
     * minPriceManwon/maxPriceManwon은 APT 주택형별 최고 공급금액 중 최저·최고값이다. 최저 분양가가 아니다.
     * receiptChannel: 접수처. LH청약플러스에서 접수하는 APT 공고면 "LH", 청약홈 접수면 null(ReceiptChannel, 목록 응답과 같은 판정).
     */
    public record Summary(String id, String houseNm, String address, String supply, String housingType,
            LocalDate startDate, LocalDate endDate, LocalDate winnerDate, String priceSummary, List<Event> events, boolean unavailable,
            Long minPriceManwon, Long maxPriceManwon, String moveInMonth, String receiptChannel) {
        /** 접수처 없이 만드는 모양(청약홈 접수로 본다). */
        public Summary(String id, String houseNm, String address, String supply, String housingType, LocalDate startDate, LocalDate endDate, LocalDate winnerDate,
                String priceSummary, List<Event> events, boolean unavailable, Long minPriceManwon, Long maxPriceManwon, String moveInMonth) {
            this(id, houseNm, address, supply, housingType, startDate, endDate, winnerDate, priceSummary, events, unavailable, minPriceManwon, maxPriceManwon, moveInMonth, null);
        }
        /** 입주 예정월 없이 만드는 기존 모양(캘린더·테스트용). */
        public Summary(String id, String houseNm, String address, String supply, String housingType, LocalDate startDate, LocalDate endDate, LocalDate winnerDate,
                String priceSummary, List<Event> events, boolean unavailable, Long minPriceManwon, Long maxPriceManwon) {
            this(id, houseNm, address, supply, housingType, startDate, endDate, winnerDate, priceSummary, events, unavailable, minPriceManwon, maxPriceManwon, null, null);
        }
        /** 원본 공고가 사라진 저장 항목을 화면·메일·캘린더에서 같은 모양으로 표시한다. */
        public static Summary missing(String id) { return new Summary(id,"원본을 찾을 수 없는 공고","","","",null,null,null,"",List.of(),true,null,null,null,null); }
    }
    /**
     * 원본 공고를 읽어 요약을 만든다. 공고가 없으면 AnnouncementService가 EntityNotFoundException을 던진다.
     * 일정은 접수·발표·특별공급·순위별·계약 날짜를 모은 뒤 ScheduleEvents로 같은 날짜를 합친다.
     */
    public Summary get(String type, String house, String notice) {
        Map<String,Object> data = announcements.getDetail(AnnouncementType.fromPath(type), house, notice);
        Map<String,Object> a = mapper.convertValue(data.get("announcement"), new TypeReference<>() {});
        String id = type + "/" + house + "/" + notice;
        LocalDate start = date(value(a,"rceptBgnde","subscrptRceptBgnde"));
        LocalDate end = date(value(a,"rceptEndde","subscrptRceptEndde"));
        LocalDate winner = date(value(a,"przwnerPresnatnDe"));
        List<Event> events = new ArrayList<>();
        add(events,"접수 시작",start);
        // 상시 접수(마감일 2100년 이후 표기)는 '접수 마감' 일정으로 넣지 않는다. 캘린더 구독·일정 메일에 9999-12-31이 나오지 않게(웹 summaryOf와 같다).
        if (!com.toady.web.app.announcement.ReceiptWindow.alwaysOpen(end)) add(events,"접수 마감",end);
        add(events,"당첨자 발표",winner);
        add(events,"특별공급 시작",date(value(a,"spsplyRceptBgnde")));
        add(events,"1순위 해당지역",date(value(a,"gnrlRnk1CrspareaRcptde")));
        add(events,"1순위 경기지역",date(value(a,"gnrlRnk1EtcGgRcptde")));
        add(events,"1순위 기타지역",date(value(a,"gnrlRnk1EtcAreaRcptde")));
        add(events,"2순위 해당지역",date(value(a,"gnrlRnk2CrspareaRcptde")));
        add(events,"2순위 경기지역",date(value(a,"gnrlRnk2EtcGgRcptde")));
        add(events,"2순위 기타지역",date(value(a,"gnrlRnk2EtcAreaRcptde")));
        add(events,"계약 시작",date(value(a,"cntrctCnclsBgnde"))); add(events,"계약 마감",date(value(a,"cntrctCnclsEndde")));
        String prices="";
        Long min=null,max=null;
        if(type.equals("apt")) {
            List<Map<String,Object>> models=mapper.convertValue(data.get("models"),new TypeReference<>() {});
            prices=models.stream().filter(m -> !value(m,"lttotTopAmount").isBlank()).limit(5)
                .map(m -> value(m,"houseTy")+": "+value(m,"lttotTopAmount")+"만원 (최고가)").collect(Collectors.joining(" / "));
            LongSummaryStatistics amounts=models.stream().map(m -> manwon(value(m,"lttotTopAmount"))).filter(Objects::nonNull).mapToLong(Long::longValue).summaryStatistics();
            if(amounts.getCount()>0) { min=amounts.getMin(); max=amounts.getMax(); }
        }
        return new Summary(id,value(a,"houseNm"),value(a,"hssplyAdres","hssplyHshldco"),value(a,"totSuplyHshldco"),value(a,"houseSecdNm"),start,end,winner,prices,ScheduleEvents.normalize(events),false,min,max,
            moveInMonth(value(a,"mvnPrearngeYm")),
            // 접수처 판정은 목록(AnnouncementListMapper)과 같이 APT만 한다.
            type.equals("apt") ? ReceiptChannel.of(value(a,"bsnsMbyNm"),value(a,"hmpgAdres"),value(a,"spsplyRceptBgnde")) : null);
    }
    /** 입주 예정월 '202901' → '2029-01'. 형식이 다르면 null(당첨·계약 뒤 화면이 입주까지 남은 기간을 보여 준다). */
    static String moveInMonth(String value) {
        String digits=value==null?"":value.replaceAll("\\D","");
        if(digits.length()!=6) return null;
        int month=Integer.parseInt(digits.substring(4));
        return month>=1 && month<=12 ? digits.substring(0,4)+"-"+digits.substring(4) : null;
    }
    private static void add(List<Event> events,String label,LocalDate date) { if(date != null) events.add(new Event(label,date)); }
    // 여러 키 중 처음으로 값이 있는 것을 쓴다(공고 유형마다 같은 뜻의 필드 이름이 달라서).
    private static String value(Map<String,Object> a,String...keys) { for(String key:keys) { Object v=a.get(key); if(v!=null && !v.toString().isBlank()) return v.toString(); } return ""; }
    /** 0·음수·숫자가 아닌 금액은 공개되지 않은 값으로 본다. */
    static Long manwon(String value) { try { long amount=Long.parseLong(value.replace(",","").trim()); return amount>0?amount:null; } catch(Exception ignored) { return null; } }
    /** '2026-01-05'·'20260105' 둘 다 받는다. 읽을 수 없으면 null. */
    static LocalDate date(String value) { try { return LocalDate.parse(value.replace("-",""),DateTimeFormatter.BASIC_ISO_DATE); } catch(Exception ignored) { return null; } }
}
