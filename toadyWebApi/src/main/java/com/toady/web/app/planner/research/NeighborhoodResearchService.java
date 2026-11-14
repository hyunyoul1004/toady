package com.toady.web.app.planner.research;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 같은 법정동의 신고 거래를 제공한다. 호가나 시세 추정으로 표현하지 않는다. */
@Slf4j
@Service
public class NeighborhoodResearchService {
    /** jibun: 단지 지번(지도 표시용 위치 확인에 쓴다). 제공처가 비워 두면 빈 문자열. */
    public record Trade(String apartment,String dong,String dongCode,BigDecimal area,int floor,String buildYear,LocalDate date,long amountManwon,String jibun) {
        public Trade(String apartment,String dong,String dongCode,BigDecimal area,int floor,String buildYear,LocalDate date,long amountManwon) {
            this(apartment,dong,dongCode,area,floor,buildYear,date,amountManwon,"");
        }
    }
    public record TradeResult(String status,String bcode,String dong,String fromMonth,String toMonth,Instant checkedAt,int total,List<Trade> items) { }
    record TradeMonth(List<Trade> trades,Instant fetchedAt) { }
    record Page(int total,int rawCount,List<Trade> trades) { }
    private final ResearchTransport transport;
    private final String molitKey;
    private final Cache<String,TradeMonth> tradeCache=CacheBuilder.newBuilder().maximumWeight(150000)
        .weigher((String key,TradeMonth value)->Math.max(1,value.trades().size())).expireAfterWrite(6,TimeUnit.HOURS).build();
    public NeighborhoodResearchService(ResearchTransport transport,
            @Value("${toady.research.molit-service-key:}") String molitKey) {
        this.transport=transport;
        // 공공데이터포털의 일반/인코딩 인증키를 모두 허용하되 요청에는 한 번만 인코딩한다.
        String key=molitKey.trim();
        this.molitKey=key.contains("%")?URLDecoder.decode(key,StandardCharsets.UTF_8):key;
        // 키가 없으면 화면은 미제공 상태로만 보이므로 운영 로그에서 설정 누락을 바로 알 수 있게 남긴다.
        if(!tradesEnabled()) log.warn("Neighborhood trades are disabled: MOLIT_TRADE_SERVICE_KEY is not configured");
    }
    /** 법정동 코드 앞 2자리(시·도). 12: 전남광주통합특별시(광주·전남 통합 뒤 새 코드). */
    static final java.util.Set<String> SIDO_CODES=java.util.Set.of("11","12","26","27","28","29","30","31","36","41","42","43","44","45","46","47","48","50","51","52");
    private final Cache<String,Boolean> failedMonths=CacheBuilder.newBuilder()
        // 제공처 일시 오류(초당 호출 제한 등)로 한 달이 실패해도 그 달만 잠깐(30초) 쉬고, 같은 시·군·구의 다른 달은 계속 쓴다.
        .maximumSize(5000).expireAfterWrite(FAILED_MONTH_SECONDS,TimeUnit.SECONDS).build();
    static final int FAILED_MONTH_SECONDS=30;
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
    /** q1·q3: 1·3사분위(가운데 절반 범위). new*: 준공 10년 이내 거래만의 값(거래가 없으면 0). */
    public record AreaStat(BigDecimal area,String scope,int count,long median,long min,long max,long q1,long q3,long newMedian,int newCount,long newQ1,long newQ3) { }
    /** months: 실제로 반영한 개월 수. partial: 응답 시간 한도로 6개월을 다 받지 못했음(화면은 짧게 캐시한다). */
    public record GapResult(String status,String bcode,String dong,String fromMonth,String toMonth,Instant checkedAt,List<AreaStat> areas,int months,boolean partial) {
        GapResult(String status,String bcode,String dong,String fromMonth,String toMonth,Instant checkedAt,List<AreaStat> areas) { this(status,bcode,dong,fromMonth,toMonth,checkedAt,areas,0,false); }
    }
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
                // 최근 실패한 달은 건너뛰고(부분 결과), 나머지 달로 비교한다.
                if(failedMonths.getIfPresent(key)!=null) continue;
                TradeMonth data=tradeCache.getIfPresent(key);
                if(data==null) {
                    // 응답 시간 한도를 넘기면 제공처 실패로 기록하지 않고, 이미 받은 달까지만으로 결과를 만든다.
                    if(System.nanoTime()>deadline) break;
                    try { data=tradeCache.get(key,()->loadMonth(bcode.substring(0,5),ymd,deadline)); }
                    catch(Exception failure) {
                        if(deadlineExceeded(failure)) break;
                        failedMonths.put(key,Boolean.TRUE); continue;
                    }
                }
                all.addAll(data.trades());updated.add(data.fetchedAt());earliest=end.minusMonths(month);
            }
            // 받은 달이 2개월도 안 되면 비교하지 않는다(실패한 달은 건너뛰므로 '최근' 2개월이 아닐 수 있다 — partial로 알린다).
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
                if(chosen.isEmpty()) { stats.add(new AreaStat(area,scope,0,0,0,0,0,0,0,0,0,0)); continue; }
                List<Long> amounts=chosen.stream().map(Trade::amountManwon).sorted().toList();
                List<Long> fresh=chosen.stream().filter(t -> t.buildYear().matches("\\d{4}") && thisYear-Integer.parseInt(t.buildYear())<=NEW_BUILD_YEARS).map(Trade::amountManwon).sorted().toList();
                stats.add(new AreaStat(area,scope,amounts.size(),median(amounts),amounts.get(0),amounts.get(amounts.size()-1),quantile(amounts,0.25),quantile(amounts,0.75),
                    fresh.isEmpty()?0:median(fresh),fresh.size(),fresh.isEmpty()?0:quantile(fresh,0.25),fresh.isEmpty()?0:quantile(fresh,0.75)));
            }
            boolean any=stats.stream().anyMatch(stat -> stat.count()>0);
            return new GapResult(any?"READY":"EMPTY",bcode,dong,start.toString(),end.toString(),updated.stream().min(Comparator.naturalOrder()).orElse(null),stats,updated.size(),updated.size()<GAP_MONTHS);
        } catch(Exception failure) {return new GapResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,List.of());}
    }
    /** 시·군·구 전체의 최근 거래. months: 받은 개월 수, partial: 응답 시간 한도로 일부 달만 받음(받은 달은 캐시되어 다음 요청이 이어 받는다). */
    public record DistrictTrades(String status,String fromMonth,String toMonth,Instant checkedAt,List<Trade> trades,int months,boolean partial) { }
    public DistrictTrades districtTrades(String bcode,int months,long deadline) {
        YearMonth end=YearMonth.now(ZoneId.of("Asia/Seoul")),start=end.minusMonths(months-1);
        if(!tradesEnabled()) return new DistrictTrades("NOT_CONFIGURED",start.toString(),end.toString(),null,List.of(),0,false);
        // 지원하지 않는 시·도 코드는 일시 오류(UNAVAILABLE)와 구분한다. 화면은 '다시 시도' 대신 '아직 지원하지 않는 지역'으로 안내한다.
        if(bcode==null || !bcode.matches("\\d{10}") || !SIDO_CODES.contains(bcode.substring(0,2))) return new DistrictTrades("UNSUPPORTED",start.toString(),end.toString(),null,List.of(),0,false);
        try {
            List<Trade> all=new ArrayList<>();List<Instant> updated=new ArrayList<>();YearMonth earliest=end;
            for(int month=0;month<months;month++) {
                String ymd=end.minusMonths(month).format(DateTimeFormatter.ofPattern("yyyyMM"));
                String key=bcode.substring(0,5)+"/"+ymd;
                // 한 달이 실패해도 시·군·구 전체를 막지 않는다: 그 달만 건너뛰고 partial로 돌려주면 화면이 잠시 뒤 다시 부른다.
                if(failedMonths.getIfPresent(key)!=null) continue;
                TradeMonth data=tradeCache.getIfPresent(key);
                if(data==null) {
                    if(System.nanoTime()>deadline) break;
                    try { data=tradeCache.get(key,()->loadMonth(bcode.substring(0,5),ymd,deadline)); }
                    catch(Exception failure) { if(deadlineExceeded(failure)) break; failedMonths.put(key,Boolean.TRUE); continue; }
                }
                all.addAll(data.trades());updated.add(data.fetchedAt());earliest=end.minusMonths(month);
            }
            if(updated.isEmpty()) return new DistrictTrades("UNAVAILABLE",start.toString(),end.toString(),null,List.of(),0,true);
            return new DistrictTrades(all.isEmpty()?"EMPTY":"READY",earliest.toString(),end.toString(),updated.stream().min(Comparator.naturalOrder()).orElse(null),all,updated.size(),updated.size()<months);
        } catch(Exception failure) {return new DistrictTrades("UNAVAILABLE",start.toString(),end.toString(),null,List.of(),0,false);}
    }
    /**
     * 시·군·구의 한 달 거래를 캐시에 넣지 않고 받는다(1년보다 오래된 달은 단지별 요약만 남기려고). 실패하면 빈 Optional.
     * 최근 실패한 달은 잠시 다시 부르지 않는다.
     */
    public Optional<List<Trade>> monthTrades(String lawd5,YearMonth month,long deadline) {
        if(!tradesEnabled() || lawd5==null || !lawd5.matches("\\d{5}") || !SIDO_CODES.contains(lawd5.substring(0,2))) return Optional.empty();
        String ymd=month.format(DateTimeFormatter.ofPattern("yyyyMM")),key=lawd5+"/"+ymd;
        TradeMonth cached=tradeCache.getIfPresent(key);
        if(cached!=null) return Optional.of(cached.trades());
        if(failedMonths.getIfPresent(key)!=null) return Optional.empty();
        try { return Optional.of(loadMonth(lawd5,ymd,deadline).trades()); }
        catch(Exception failure) { if(!deadlineExceeded(failure) && !(failure instanceof ProviderPaused)) failedMonths.put(key,Boolean.TRUE); return Optional.empty(); }
    }
    /**
     * 이 달을 지금 부르면 제공처에 실제로 요청하지 않고 바로 실패하는지(최근 실패해 잠시 쉬는 달이거나 제공처 호출을 멈춘 상태).
     * 이때 받은 빈 결과는 그 달의 실패로 세지 않고 다음 요청에서 다시 받는다.
     */
    public boolean monthWaiting(String lawd5,YearMonth month) {
        if(lawd5==null) return false;
        String key=lawd5+"/"+month.format(DateTimeFormatter.ofPattern("yyyyMM"));
        if(tradeCache.getIfPresent(key)!=null) return false;
        return molitPaused() || failedMonths.getIfPresent(key)!=null;
    }
    /** 정렬된 값의 p 분위(선형 보간, 만원 반올림). */
    static long quantile(List<Long> sorted,double p) {
        if(sorted.size()==1) return sorted.get(0);
        double index=p*(sorted.size()-1);int low=(int)Math.floor(index),high=(int)Math.ceil(index);
        return Math.round(sorted.get(low)+(sorted.get(high)-sorted.get(low))*(index-low));
    }
    static long median(List<Long> sorted) {
        int middle=sorted.size()/2;
        return sorted.size()%2==1?sorted.get(middle):Math.round((sorted.get(middle-1)+sorted.get(middle))/2.0);
    }
    /**
     * 한 쪽을 받는다. 통신 오류(시간 초과·연결 끊김)만 0.4초 뒤 한 번 다시 시도한다. 제공처가 거절한 응답(키·쿼터 등)은
     * 다시 불러도 같으므로 바로 실패로 둔다. 다시 시도는 남은 시간이 한 번 더 받을 만큼(약 4초) 있을 때만 한다.
     */
    private Page fetchPage(URI uri,long deadline) throws Exception {
        long pausedUntil=molitPausedUntil;
        if(System.currentTimeMillis()<pausedUntil) throw new ProviderPaused();
        byte[] body;
        try { body=transport.get(uri,Map.of()); }
        catch(Exception first) {
            // 429(요청 과다)·4xx는 다시 불러도 같고 한도를 더 쓰므로 재시도하지 않는다.
            boolean refused=first instanceof ResearchTransport.ProviderStatus status && !status.retryable();
            if(first instanceof ResearchTransport.ProviderStatus status && Set.of(401,403,429).contains(status.status()))
                pauseMolit(status.status()==429?Duration.ofMinutes(10):Duration.ofHours(1),"HTTP "+status.status());
            if(refused || first instanceof InterruptedException || System.nanoTime()+TimeUnit.SECONDS.toNanos(RETRY_NEEDS_SECONDS)>deadline) throw first;
            Thread.sleep(400);
            body=transport.get(uri,Map.of());
        }
        try { return parseTrades(body); }
        catch(ProviderRejected rejected) {
            // 일일 한도 초과(22)는 자정(한국시간)에 풀리고, 접근 거부·키 오류(20·30·31·32)는 운영자가 고칠 때까지 1시간씩 쉰다.
            // 그 밖의 코드는 그 달만 실패로 둔다.
            if("22".equals(rejected.code)) pauseMolit(Duration.between(ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul")),
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))),"resultCode 22");
            else if(Set.of("20","30","31","32").contains(rejected.code)) pauseMolit(Duration.ofHours(1),"resultCode "+rejected.code);
            throw rejected;
        }
    }
    /**
     * 국토부가 한도 초과·인증 오류로 거절하면 모든 지역·달의 호출을 잠시 멈춘다(차단기). 지역·달마다 따로 실패를
     * 기억하면 화면 재조회와 미리 계산 작업이 시·군·구 수 × 12개월만큼 계속 호출해 한도를 더 소모한다.
     */
    private volatile long molitPausedUntil;
    private void pauseMolit(Duration duration,String reason) {
        long until=System.currentTimeMillis()+Math.max(60_000,duration.toMillis());
        if(until>molitPausedUntil) {
            molitPausedUntil=until;
            LoggerFactory.getLogger(NeighborhoodResearchService.class).warn("국토부 실거래 호출을 {}분 멈춤: {}",Math.max(1,duration.toMinutes()),reason);
        }
    }
    /** 차단기가 열려 있는지(화면 안내·테스트용). */
    public boolean molitPaused() { return System.currentTimeMillis()<molitPausedUntil; }
    static final class ProviderPaused extends IllegalStateException { ProviderPaused() { super("Provider paused after rejection"); } }
    static final class ProviderRejected extends IllegalStateException {
        final String code;
        ProviderRejected(String code) { super("Provider rejected request: "+code); this.code=code; }
    }
    static final int RETRY_NEEDS_SECONDS=4;
    private TradeMonth loadMonth(String district,String ymd,long deadline) throws Exception {
        List<Trade> result=new ArrayList<>();int expected=-1,read=0;
        for(int number=1;number<=5;number++) {
            if(System.nanoTime()>deadline) throw new DeadlineExceeded();
            URI uri=URI.create("https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade?LAWD_CD="+district+"&DEAL_YMD="+ymd+"&numOfRows=1000&pageNo="+number+"&serviceKey="+encode(molitKey));
            Page page=fetchPage(uri,deadline);
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
        if(!Set.of("00","000").contains(code)) throw new ProviderRejected(code);
        int total=Integer.parseInt(text(doc.getDocumentElement(),"totalCount"));
        NodeList items=doc.getElementsByTagName("item");List<Trade> trades=new ArrayList<>();
        for(int i=0;i<items.getLength();i++) {
            Element row=(Element)items.item(i);
            String cancelled=text(row,"cdealType"),cancelDate=text(row,"cdealDay");
            if(cancelled.equalsIgnoreCase("Y") || (!cancelDate.isBlank() && !cancelDate.equals("-"))) continue;
            // 값이 비었거나 형식이 틀린 한 줄 때문에 그 달 전체를 버리지 않는다(그 줄만 빼고, 쪽 수 계산은 rawCount로 그대로 맞춘다).
            try {
                String name=text(row,"aptNm"),dong=text(row,"umdNm"),dongCode=text(row,"umdCd");
                long amount=Long.parseLong(text(row,"dealAmount").replace(",",""));
                BigDecimal area=new BigDecimal(text(row,"excluUseAr"));
                if(name.isBlank() || dong.isBlank() || amount<=0 || area.signum()<=0) continue;
                LocalDate date=LocalDate.of(Integer.parseInt(text(row,"dealYear")),Integer.parseInt(text(row,"dealMonth")),Integer.parseInt(text(row,"dealDay")));
                trades.add(new Trade(name,dong,dongCode,area,Integer.parseInt(text(row,"floor")),text(row,"buildYear"),date,amount,text(row,"jibun")));
            } catch(NumberFormatException|DateTimeException invalid) { /* 이 줄만 건너뛴다 */ }
        }
        return new Page(total,items.getLength(),List.copyOf(trades));
    }
    private static String text(Element element,String name) {NodeList nodes=element.getElementsByTagName(name);return nodes.getLength()==0?"":nodes.item(0).getTextContent().trim();}
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
