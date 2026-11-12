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
    public boolean tradesEnabled() {return !molitKey.isBlank();}
    public TradeResult trades(String bcode,String dong) {return trades(bcode,dong,null);}
    public TradeResult trades(String bcode,String dong,BigDecimal area) {
        YearMonth end=YearMonth.now(ZoneId.of("Asia/Seoul")),start=end.minusMonths(2);
        if(!tradesEnabled()) return new TradeResult("NOT_CONFIGURED",bcode,dong,start.toString(),end.toString(),null,0,List.of());
        try {
            List<Trade> all=new ArrayList<>();List<Instant> updated=new ArrayList<>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
            for(int month=0;month<3;month++) {
                String ymd=end.minusMonths(month).format(DateTimeFormatter.ofPattern("yyyyMM"));
                TradeMonth data=tradeCache.get(bcode.substring(0,5)+"/"+ymd,()->loadMonth(bcode.substring(0,5),ymd,deadline));
                all.addAll(data.trades());updated.add(data.fetchedAt());
            }
            String legalDong=bcode.substring(5);
            List<Trade> matched=all.stream().filter(t -> t.dongCode().isBlank()? t.dong().equals(dong):t.dongCode().equals(legalDong))
                .filter(t -> area==null || t.area().subtract(area).abs().compareTo(BigDecimal.valueOf(5))<=0)
                .sorted(Comparator.comparing(Trade::date).reversed().thenComparing(Trade::apartment).thenComparing(Trade::area)).toList();
            return new TradeResult(matched.isEmpty()?"EMPTY":"READY",bcode,dong,start.toString(),end.toString(),updated.stream().min(Comparator.naturalOrder()).orElse(null),matched.size(),matched.stream().limit(20).toList());
        } catch(Exception failure) {return new TradeResult("UNAVAILABLE",bcode,dong,start.toString(),end.toString(),null,0,List.of());}
    }
    private TradeMonth loadMonth(String district,String ymd,long deadline) throws Exception {
        List<Trade> result=new ArrayList<>();int expected=-1,read=0;
        for(int number=1;number<=5;number++) {
            if(System.nanoTime()>deadline) throw new IllegalStateException("Provider deadline");
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
