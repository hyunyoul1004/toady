package com.toady.web.app.announcement.document;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 개별 분양 사이트 대신 청약홈 공통 상세 양식에 공개된 원문 첨부 링크만 제공한다. */
@Service
public class NoticeDocumentService {
    public record Document(String title, String url) { }
    public record Result(String status, List<Document> documents) { }
    private final NoticeDocumentTransport transport;
    private final Cache<String, Result> cache = CacheBuilder.newBuilder().maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS).build();
    private final Cache<String, Result> failures = CacheBuilder.newBuilder().maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES).build();

    public NoticeDocumentService(NoticeDocumentTransport transport) { this.transport = transport; }

    public Result get(String type, String house, String notice) {
        String key = type + "/" + house + "/" + notice;
        Result cached = cache.getIfPresent(key);
        if (cached != null) return cached;
        Result failed = failures.getIfPresent(key);
        if (failed != null) return failed;
        try {
            return cache.get(key, () -> {
                var links = parse(new String(transport.get(source(type, house, notice)), StandardCharsets.UTF_8), house, notice);
                return new Result(links.isEmpty() ? "EMPTY" : "READY", links);
            });
        } catch (Exception unavailable) {
            Result result = new Result("UNAVAILABLE", List.of());
            failures.put(key, result);
            return result;
        }
    }

    static URI source(String type, String house, String notice) {
        if (!house.matches("[0-9]{1,40}") || !notice.matches("[0-9]{1,40}")) throw new IllegalArgumentException("Invalid announcement ID");
        String page = switch (type) {
            case "apt" -> "selectAPTLttotPblancDetail.do";
            case "remaining" -> "selectAPTRemndrLttotPblancDetailView.do";
            case "urban", "public-rental", "optional" -> "selectPRMOLttotPblancDetailView.do";
            default -> throw new IllegalArgumentException("Unsupported announcement type");
        };
        return URI.create("https://www.applyhome.co.kr/ai/aia/" + page + "?houseManageNo=" + house + "&pblancNo=" + notice);
    }

    static List<Document> parse(String html, String house, String notice) throws Exception {
        Map<String, Document> documents = new LinkedHashMap<>();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            private String href;
            private final StringBuilder label = new StringBuilder();
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
                if (tag == HTML.Tag.A) { href = Objects.toString(attrs.getAttribute(HTML.Attribute.HREF), ""); label.setLength(0); }
            }
            @Override public void handleText(char[] text, int position) { if (href != null) label.append(text); }
            @Override public void handleEndTag(HTML.Tag tag, int position) {
                if (tag != HTML.Tag.A || href == null) return;
                String title = label.toString().replaceAll("\\s+", " ").trim();
                if (title.replaceAll("\\s+", "").contains("모집공고문")) {
                    String url = safeAttachment(href, house, notice);
                    if (url != null) documents.putIfAbsent(url, new Document(title, url));
                }
                href = null;
            }
        }, true);
        return List.copyOf(documents.values());
    }

    static String safeAttachment(String href, String house, String notice) {
        try {
            URI uri = URI.create("https://www.applyhome.co.kr").resolve(href);
            if (!"https".equals(uri.getScheme()) || !Set.of("www.applyhome.co.kr", "static.applyhome.co.kr").contains(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
                    || !"/ai/aia/getAtchmnfl.do".equals(uri.getPath()) || uri.getRawQuery() == null) return null;
            Map<String, String> query = new HashMap<>();
            for (String part : uri.getRawQuery().split("&")) {
                String[] pair = part.split("=", -1);
                if (pair.length != 2) return null;
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8), value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (!value.matches("[0-9]{1,40}") || query.putIfAbsent(key, value) != null) return null;
            }
            if (!query.keySet().equals(Set.of("houseManageNo", "pblancNo", "atchmnflSeqNo", "atchmnflSn"))
                    || !house.equals(query.get("houseManageNo")) || !notice.equals(query.get("pblancNo"))) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException invalid) { return null; }
    }
}
