package com.toady.web.app.planner.research;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 같은 법정동의 신고 거래를 제공한다. 호가나 시세 추정으로 표현하지 않는다. */
@Slf4j
@Service
public class NeighborhoodResearchService {
    public record Trade(String apartment,String dong,String dongCode,BigDecimal area,int floor,String buildYear,LocalDate date,long amountManwon) { }
    public record TradeResult(String status,String bcode,String dong,String fromMonth,String toMonth,Instant checkedAt,int total,List<Trade> items) { }
    record TradeMonth(List<Trade> trades,Instant fetchedAt) { }
    record Page(int total,int rawCount,List<Trade> trades) { }
    private final ResearchTransport transport;
    private final String molitKey;
    private final Cache<String,TradeMonth> tradeCache=CacheBuilder.newBuilder().maximumWeight(50000)
        .weigher((String key,TradeMonth value)->Math.max(1,value.trades().size())).expireAfterWrite(6,TimeUnit.HOURS).build();
    public NeighborhoodResearchService(ResearchTransport transport,
            @Value("${toady.research.molit-service-key:}") String molitKey) {
        this.transport=transport;
        // 공공데이터포털의 일반/인코딩 인증키를 모두 허용하되 요청에는 한 번만 인코딩한다.
        String key=molitKey.trim();
        this.molitKey=key.contains("%")?java.net.URLDecoder.decode(key,StandardCharsets.UTF_8):key;
        // 키가 없으면 화면은 미제공 상태로만 보이므로 운영 로그에서 설정 누락을 바로 알 수 있게 남긴다.
        if(!tradesEnabled()) log.warn("Neighborhood trades are disabled: MOLIT_TRADE_SERVICE_KEY is not configured");
    }
    static final java.util.Set<String> SIDO_CODES=java.util.Set.of("11","26","27","28","29","30","31","36","41","42","43","44","45","46","47","48","50","51","52");
    private final com.google.common.cache.Cache<String,Boolean> failedMonths=com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(5000).expireAfterWrite(3,TimeUnit.MINUTES).build();
    public boolean tradesEnabled() {return !molitKey.isBlank();}
    public TradeResult trades(String bcode,String dong) {return trades(bcode,dong,null);}
    public TradeResult trades(String bcode,String dong,BigDecimal area) {
        YearMonth end=YearMonth.now(ZoneId.of("Asia/Seoul")),start=end.minusMonths(2);
        if(!tradesEnabled()) return new TradeResult("NOT_CONFIGURED",bcode,dong,start.toString(),end.toString(),null,0,List.of());
        // 법정동 코드 앞 2자리는 시·도 코드다. 없는 시·도는 외부 호출 없이 거절한다.
        if(bcode==null || !bcode.matches("\\d{10}") || !SIDO_CODES.contains(bcode.substring(0,2))) return new TradeResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,0,List.of());
        try {
            List<Trade> all=new ArrayList<>();List<Instant> updated=new ArrayList<>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
            for(int month=0;month<3;month++) {
                String ymd=end.minusMonths(month).format(DateTimeFormatter.ofPattern("yyyyMM"));
                String key=bcode.substring(0,5)+"/"+ymd;
                // 최근 실패한 시군구·월은 몇 분간 다시 부르지 않는다(잘못된 코드 반복으로 공공데이터 일일 쿼터를 쓰지 않게).
                if(failedMonths.getIfPresent(key)!=null) throw new IllegalStateException("Recently failed");
                TradeMonth data;
                try { data=tradeCache.get(key,()->loadMonth(bcode.substring(0,5),ymd,deadline)); }
                catch(Exception failure) { if(!deadlineExceeded(failure)) failedMonths.put(key,Boolean.TRUE); throw failure; }
                all.addAll(data.trades());updated.add(data.fetchedAt());
            }
            String legalDong=bcode.substring(5);
            List<Trade> matched=all.stream().filter(t -> t.dongCode().isBlank()? t.dong().equals(dong):t.dongCode().equals(legalDong))
                .filter(t -> area==null || t.area().subtract(area).abs().compareTo(BigDecimal.valueOf(5))<=0)
                .sorted(Comparator.comparing(Trade::date).reversed().thenComparing(Trade::apartment).thenComparing(Trade::area)).toList();
            return new TradeResult(matched.isEmpty()?"EMPTY":"READY",bcode,dong,start.toString(),end.toString(),updated.stream().min(Comparator.naturalOrder()).orElse(null),matched.size(),matched.stream().limit(20).toList());
        } catch(Exception failure) {return new TradeResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,0,List.of());}
    }
    /** 주택형 면적 하나의 실거래 요약. scope: DONG(같은 법정동) · DISTRICT(같은 시·군·구, 같은 동 거래가 적을 때). */
    public record AreaStat(BigDecimal area,String scope,int count,long median,long min,long max,long newMedian,int newCount) { }
    public record GapResult(String status,String bcode,String dong,String fromMonth,String toMonth,Instant checkedAt,List<AreaStat> areas) { }
    static final int GAP_MONTHS=6, MIN_GAP_MONTHS=2, ENOUGH_TRADES=3, NEW_BUILD_YEARS=10;
    /** 이 서비스가 정한 응답 시간 한도 초과(제공처 거절·오류와 구분). */
    static final class DeadlineExceeded extends IllegalStateException { DeadlineExceeded() { super("Provider deadline"); } }
    private static boolean deadlineExceeded(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof DeadlineExceeded) return true;
        return false;
    }

    /**
     * 분양가와 나란히 볼 면적별 실거래 중간값. 최근 6개월, 전용 ±5㎡.
     * 같은 법정동 거래가 3건 미만이면 같은 시·군·구로 넓히고 그 사실을 scope로 알린다. 준공 10년 이내 거래만의 중간값도 함께 준다.
     */
    public GapResult priceGap(String bcode,String dong,List<BigDecimal> areas) {
        YearMonth end=YearMonth.now(ZoneId.of("Asia/Seoul")),start=end.minusMonths(GAP_MONTHS-1);
        if(!tradesEnabled()) return new GapResult("NOT_CONFIGURED",bcode,dong,start.toString(),end.toString(),null,List.of());
        if(bcode==null || !bcode.matches("\\d{10}") || !SIDO_CODES.contains(bcode.substring(0,2))) return new GapResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,List.of());
        try {
            List<Trade> all=new ArrayList<>();List<Instant> updated=new ArrayList<>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            YearMonth earliest=end;
            for(int month=0;month<GAP_MONTHS;month++) {
                String ymd=end.minusMonths(month).format(DateTimeFormatter.ofPattern("yyyyMM"));
                String key=bcode.substring(0,5)+"/"+ymd;
                if(failedMonths.getIfPresent(key)!=null) throw new IllegalStateException("Recently failed");
                TradeMonth data=tradeCache.getIfPresent(key);
                if(data==null) {
                    // 응답 시간 한도를 넘기면 제공처 실패로 기록하지 않고, 이미 받은 달까지만으로 결과를 만든다.
                    if(System.nanoTime()>deadline) break;
                    try { data=tradeCache.get(key,()->loadMonth(bcode.substring(0,5),ymd,deadline)); }
                    catch(Exception failure) {
                        if(deadlineExceeded(failure)) break;
                        failedMonths.put(key,Boolean.TRUE); throw failure;
                    }
                }
                all.addAll(data.trades());updated.add(data.fetchedAt());earliest=end.minusMonths(month);
            }
            // 최근 2개월도 받지 못했으면 비교하지 않는다.
            if(updated.size()<MIN_GAP_MONTHS) return new GapResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,List.of());
            start=earliest;
            String legalDong=bcode.substring(5);
            int thisYear=end.getYear();
            List<AreaStat> stats=new ArrayList<>();
            for(BigDecimal area:areas.stream().distinct().limit(8).toList()) {
                List<Trade> near=all.stream().filter(t -> t.area().subtract(area).abs().compareTo(BigDecimal.valueOf(5))<=0).toList();
                List<Trade> sameDong=near.stream().filter(t -> t.dongCode().isBlank()? t.dong().equals(dong):t.dongCode().equals(legalDong)).toList();
                String scope=sameDong.size()>=ENOUGH_TRADES?"DONG":"DISTRICT";
                List<Trade> chosen="DONG".equals(scope)?sameDong:near;
                if(chosen.isEmpty()) { stats.add(new AreaStat(area,scope,0,0,0,0,0,0)); continue; }
                List<Long> amounts=chosen.stream().map(Trade::amountManwon).sorted().toList();
                List<Long> fresh=chosen.stream().filter(t -> t.buildYear().matches("\\d{4}") && thisYear-Integer.parseInt(t.buildYear())<=NEW_BUILD_YEARS).map(Trade::amountManwon).sorted().toList();
                stats.add(new AreaStat(area,scope,amounts.size(),median(amounts),amounts.get(0),amounts.get(amounts.size()-1),fresh.isEmpty()?0:median(fresh),fresh.size()));
            }
            boolean any=stats.stream().anyMatch(stat -> stat.count()>0);
            return new GapResult(any?"READY":"EMPTY",bcode,dong,start.toString(),end.toString(),updated.stream().min(Comparator.naturalOrder()).orElse(null),stats);
        } catch(Exception failure) {return new GapResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,List.of());}
    }
    static long median(List<Long> sorted) {
        int middle=sorted.size()/2;
        return sorted.size()%2==1?sorted.get(middle):Math.round((sorted.get(middle-1)+sorted.get(middle))/2.0);
    }
    private TradeMonth loadMonth(String district,String ymd,long deadline) throws Exception {
        List<Trade> result=new ArrayList<>();int expected=-1,read=0;
        for(int number=1;number<=5;number++) {
            if(System.nanoTime()>deadline) throw new DeadlineExceeded();
            URI uri=URI.create("https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade?LAWD_CD="+district+"&DEAL_YMD="+ymd+"&numOfRows=1000&pageNo="+number+"&serviceKey="+encode(molitKey));
            Page page=parseTrades(transport.get(uri,Map.of()));
            if(expected==-1) expected=page.total();
            if(expected!=page.total() || expected>5000) throw new IllegalStateException("Incomplete provider response");
            if(page.trades().stream().anyMatch(trade -> !YearMonth.from(trade.date()).format(DateTimeFormatter.ofPattern("yyyyMM")).equals(ymd)))
                throw new IllegalStateException("Unexpected trade month");
            read+=page.rawCount();result.addAll(page.trades());
            if(read==expected) return new TradeMonth(List.copyOf(result),Instant.now());
            if(page.rawCount()==0 || read>expected) throw new IllegalStateException("Incomplete provider page");
        }
        throw new IllegalStateException("Provider page limit");
    }
    /** 외부 XML의 DTD/외부 엔티티를 금지한다. 취소 거래는 가격 비교에서 제외한다. */
    static Page parseTrades(byte[] xml) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        String code=text(doc.getDocumentElement(),"resultCode");
        if(!Set.of("00","000").contains(code)) throw new IllegalStateException("Provider rejected request");
        int total=Integer.parseInt(text(doc.getDocumentElement(),"totalCount"));
        NodeList items=doc.getElementsByTagName("item");List<Trade> trades=new ArrayList<>();
        for(int i=0;i<items.getLength();i++) {
            Element row=(Element)items.item(i);
            String cancelled=text(row,"cdealType"),cancelDate=text(row,"cdealDay");
            if(cancelled.equalsIgnoreCase("Y") || (!cancelDate.isBlank() && !cancelDate.equals("-"))) continue;
            String name=text(row,"aptNm"),dong=text(row,"umdNm"),dongCode=text(row,"umdCd");
            long amount=Long.parseLong(text(row,"dealAmount").replace(",",""));
            BigDecimal area=new BigDecimal(text(row,"excluUseAr"));
            if(name.isBlank() || dong.isBlank() || amount<=0 || area.signum()<=0) throw new IllegalStateException("Invalid trade row");
            LocalDate date=LocalDate.of(Integer.parseInt(text(row,"dealYear")),Integer.parseInt(text(row,"dealMonth")),Integer.parseInt(text(row,"dealDay")));
            trades.add(new Trade(name,dong,dongCode,area,Integer.parseInt(text(row,"floor")),text(row,"buildYear"),date,amount));
        }
        return new Page(total,items.getLength(),List.copyOf(trades));
    }
    private static String text(Element element,String name) {NodeList nodes=element.getElementsByTagName(name);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
