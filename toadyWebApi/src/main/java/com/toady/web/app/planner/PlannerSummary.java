package com.toady.web.app.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.service.AnnouncementService;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 화면·메일·비교에서 공통으로 사용하는 원본 공고 요약. 클라이언트의 제목/날짜를 신뢰하지 않는다. */
@Service
public class PlannerSummary {
    private final AnnouncementService announcements;
    private final ObjectMapper mapper;
    public PlannerSummary(AnnouncementService announcements, ObjectMapper mapper) { this.announcements = announcements; this.mapper = mapper; }
    public record Event(String label, LocalDate date) { }
    public record Summary(String id, String houseNm, String address, String supply, String housingType,
            LocalDate startDate, LocalDate endDate, LocalDate winnerDate, String priceSummary, List<Event> events, boolean unavailable) { }
    public Summary get(String type, String house, String notice) {
        Map<String,Object> data = announcements.getDetail(AnnouncementType.fromPath(type), house, notice);
        Map<String,Object> a = mapper.convertValue(data.get("announcement"), new TypeReference<>() {});
        String id = type + "/" + house + "/" + notice;
        LocalDate start = date(value(a,"rceptBgnde","subscrptRceptBgnde"));
        LocalDate end = date(value(a,"rceptEndde","subscrptRceptEndde"));
        LocalDate winner = date(value(a,"przwnerPresnatnDe"));
        List<Event> events = new ArrayList<>();
        add(events,"접수 시작",start); add(events,"접수 마감",end); add(events,"당첨자 발표",winner);
        add(events,"특별공급 시작",date(value(a,"spsplyRceptBgnde")));
        add(events,"1순위 해당지역",date(value(a,"gnrlRnk1CrspareaRcptde")));
        add(events,"1순위 경기지역",date(value(a,"gnrlRnk1EtcGgRcptde")));
        add(events,"1순위 기타지역",date(value(a,"gnrlRnk1EtcAreaRcptde")));
        add(events,"2순위 해당지역",date(value(a,"gnrlRnk2CrspareaRcptde")));
        add(events,"2순위 경기지역",date(value(a,"gnrlRnk2EtcGgRcptde")));
        add(events,"2순위 기타지역",date(value(a,"gnrlRnk2EtcAreaRcptde")));
        add(events,"계약 시작",date(value(a,"cntrctCnclsBgnde"))); add(events,"계약 마감",date(value(a,"cntrctCnclsEndde")));
        String prices="";
        if(type.equals("apt")) {
            List<Map<String,Object>> models=mapper.convertValue(data.get("models"),new TypeReference<>() {});
            prices=models.stream().filter(m -> !value(m,"lttotTopAmount").isBlank()).limit(5)
                .map(m -> value(m,"houseTy")+": "+value(m,"lttotTopAmount")+"만원 (최고가)").collect(java.util.stream.Collectors.joining(" / "));
        }
        return new Summary(id,value(a,"houseNm"),value(a,"hssplyAdres","hssplyHshldco"),value(a,"totSuplyHshldco"),value(a,"houseSecdNm"),start,end,winner,prices,ScheduleEvents.normalize(events),false);
    }
    private static void add(List<Event> events,String label,LocalDate date) { if(date != null) events.add(new Event(label,date)); }
    private static String value(Map<String,Object> a,String...keys) { for(String key:keys) { Object v=a.get(key); if(v!=null && !v.toString().isBlank()) return v.toString(); } return ""; }
    static LocalDate date(String value) { try { return LocalDate.parse(value.replace("-",""),DateTimeFormatter.BASIC_ISO_DATE); } catch(Exception ignored) { return null; } }
}
