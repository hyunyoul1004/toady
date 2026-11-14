package com.toady.web.app.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.toady.web.app.planner.PublicHtml.esc;

/**
 * 청약 자주 묻는 질문. 앱 화면(/faq)은 /api/v1/public/faq-items의 JSON으로 그리고,
 * 검색 봇은 같은 문장을 서버 HTML(/help/faq, FAQPage 구조화 데이터 포함)로 읽는다. 대표 주소는 /help/faq다.
 * 제도는 바뀔 수 있으므로 답마다 기준과 확인할 곳을 함께 적고, 화면과 FAQPage 구조화 데이터가 같은 문장을 쓴다.
 * 답의 [문구](/경로)는 같은 사이트 링크로 바꾼다(외부 링크는 청약홈만 허용).
 */
@RestController
public class PublicFaqPage {
    public record Faq(String id, String question, String answer) { }
    public record Group(String id, String title, List<Faq> items) { }

    static final List<Group> GROUPS = List.of(
        new Group("basics", "청약 기초", List.of(
            new Faq("rank1", "청약 1순위가 되려면 어떤 조건이 필요한가요?",
                "청약통장 가입 기간이 수도권은 1년, 비수도권은 6개월 이상이어야 해요. 투기과열지구·조정대상지역(청약과열지역) 공고는 2년 이상이 필요해요. "
                + "민영주택은 지역·면적별 예치금을, 국민주택은 납입 인정 횟수(수도권 12회, 비수도권 6회, 규제지역 24회)를 함께 채워야 해요. "
                + "규제지역 공고는 여기에 더해 세대주여야 하고, 5년 안에 세대원이 당첨된 적이 없어야 하며, 민영주택은 2주택 이상 세대가 아니어야 해요."),
            new Faq("deposit", "민영주택 예치금은 얼마가 있어야 하나요?",
                "입주자모집공고일 기준으로 주민등록상 사는 지역과 신청할 주택의 전용면적에 따라 정해져요. "
                + "(전용 85㎡ 이하 · 102㎡ 이하 · 135㎡ 이하 · 모든 면적 순)\n"
                + "서울·부산: 300만원 · 600만원 · 1,000만원 · 1,500만원\n"
                + "그 밖의 광역시: 250만원 · 400만원 · 700만원 · 1,000만원\n"
                + "그 밖의 시·군: 200만원 · 300만원 · 400만원 · 500만원\n"
                + "공고일 전날까지 예치금이 들어 있어야 하니 미리 확인하세요."),
            new Faq("householder", "세대주가 아니어도 청약할 수 있나요?",
                "투기과열지구·조정대상지역이 아닌 곳의 공고는 세대원도 1순위로 신청할 수 있어요. 다만 국민주택은 세대원 모두 무주택이어야 해요. "
                + "규제지역 공고의 1순위와 노부모부양 특별공급은 세대주만 신청할 수 있어요."),
            new Faq("score", "가점제와 추첨제는 무엇이 다른가요?",
                "가점제는 무주택 기간(32점)·부양가족 수(35점)·통장 가입 기간(17점)을 합한 84점 만점 점수 순으로 당첨자를 정해요. "
                + "추첨제는 무작위 추첨이에요. 지역과 면적에 따라 가점제와 추첨제 비율이 다르고, 집이 있는 사람은 추첨 물량만 노릴 수 있어요. "
                + "내 점수는 [가점 계산기](/score)에서 계산하고 지역별 공개 당첨가점과 비교해 볼 수 있어요."),
            new Faq("residence", "다른 지역 공고에도 신청할 수 있나요?",
                "공고마다 해당지역 거주자에게 먼저 공급하고, 남는 물량을 기타지역 거주자가 신청하는 구조예요. "
                + "수도권 공고는 서울·경기·인천 거주자가 기타지역으로 신청할 수 있는 경우가 많고, 인기 지역은 해당지역 거주 기간(예: 1~2년) 요건이 붙기도 해요. "
                + "거주지역과 거주 기간은 공고마다 달라 모집공고의 '신청 자격'을 꼭 확인하세요.")
        )),
        new Group("special", "특별공급", List.of(
            new Faq("special-once", "특별공급은 몇 번까지 당첨될 수 있나요?",
                "특별공급은 원칙적으로 세대당 평생 한 번만 당첨될 수 있고, 세대원 모두 무주택이어야 신청할 수 있어요. "
                + "출산 가구 등에 대한 예외가 도입·확대되고 있으니 이미 당첨된 적이 있다면 공고의 특별공급 자격을 확인하세요."),
            new Faq("newlywed", "신혼부부 특별공급 조건은 무엇인가요?",
                "혼인신고 7년 이내 부부, 예비 신혼부부, 6세 이하 자녀를 둔 한부모 가족이 대상이에요. 세대원 모두 무주택이어야 하고 통장 가입 6개월 이상이 필요해요. "
                + "소득·자산 기준이 있어 소득 구간에 따라 우선·일반·추첨 공급으로 나뉘어요."),
            new Faq("first-life", "생애최초 특별공급 조건은 무엇인가요?",
                "세대원 모두 지금까지 집을 가져 본 적이 없어야 하고, 1순위 요건과 5년 이상 소득세 납부 이력이 필요해요. 소득·자산 기준도 있어요. "
                + "민영주택은 1인 가구도 전용 60㎡ 이하 추첨 물량에 신청할 수 있고, 국민주택은 청약저축 600만원 이상 등의 저축 요건이 있어요."),
            new Faq("multi-child", "다자녀 특별공급은 자녀가 몇 명이어야 하나요?",
                "2024년 3월 이후 공고부터 미성년 자녀(태아 포함) 2명 이상이면 신청할 수 있어요. 세대원 모두 무주택이어야 하고, 자녀 수·무주택 기간 등을 배점해 순위를 정해요."),
            new Faq("newborn", "신생아 특별공급은 누가 신청할 수 있나요?",
                "입주자모집공고일 기준으로 2세 미만 자녀가 있거나 임신 중인 무주택 세대가 신청할 수 있어요(입양 포함). 2024년 3월에 생긴 유형이며 소득·자산 기준이 있어요."),
            new Faq("income", "특별공급 소득·자산 기준은 어디서 확인하나요?",
                "소득 기준은 '전년도 도시근로자 가구원수별 월평균소득'의 몇 %인지로 정하며 매년 바뀌어요. 부동산·자동차 자산 기준도 함께 봐요. "
                + "공고의 모집공고문과 [청약홈](https://www.applyhome.co.kr)의 청약자격 안내에서 그해 기준 금액을 확인하세요.")
        )),
        new Group("zupzup", "무순위·줍줍", List.of(
            new Faq("zupzup-what", "무순위·줍줍 청약이 무엇인가요?",
                "정당계약 뒤 계약 포기나 부적격 당첨으로 남은 집을 다시 공급하는 청약이에요. 무순위(사후) 공급, 잔여세대, 계약취소주택 재공급, 임의공급이 있어요. "
                + "접수 기간이 하루 이틀로 짧아 공고가 올라오면 빨리 확인해야 해요. [무순위·줍줍 모아보기](/match/zupzup)에서 접수 중인 공고와 새 공고 알림을 받을 수 있어요."),
            new Faq("zupzup-who", "무순위 청약은 누구나 신청할 수 있나요?",
                "2025년 6월부터 무순위(사후) 청약은 원칙적으로 무주택 세대구성원인 성인만 신청할 수 있어요. 거주지역 요건은 지자체·공고마다 달라요. "
                + "계약취소주택 재공급은 해당 지역 무주택자 등 별도 조건이 있고, 임의공급은 사업주체가 정한 조건으로 받아요."),
            new Faq("rewin", "무순위로 당첨되면 다른 청약에 제한이 있나요?",
                "당첨된 주택이 규제지역이거나 분양가상한제 적용 주택이면 재당첨 제한이 걸려 일정 기간 다른 청약에 당첨될 수 없을 수 있어요. "
                + "제한 여부와 기간은 공고의 '재당첨 제한' 항목에서 확인하세요.")
        )),
        new Group("money", "당첨 후 자금", List.of(
            new Faq("payment", "계약금·중도금·잔금은 보통 얼마씩 내나요?",
                "흔히 계약금 10~20%, 중도금 60%, 잔금 20~30%로 나눠 내요. 중도금은 집단대출로 내고, 입주 때 잔금과 함께 주택담보대출로 바꿔 갚는 경우가 많아요. "
                + "대출 한도는 규제지역 여부와 소득(DSR)에 따라 달라요. 공고 상세의 '자금 계획'에서 주택형별로 입주까지 필요한 내 돈을 계산해 볼 수 있어요."),
            new Faq("acquisition-tax", "취득세는 얼마나 나오나요?",
                "1주택 기준으로 6억원 이하는 1%, 6억~9억원은 금액에 따라 1~3%, 9억원 초과는 3%이고 지방교육세 등이 더해져요. "
                + "여러 채를 가지게 되면 중과세율이 적용될 수 있어요. 정확한 세액은 잔금일·보유 주택 수에 따라 달라 세무 상담이나 지자체 안내를 확인하세요.")
        )),
        new Group("toady", "TOADY 이용", List.of(
            new Faq("about", "TOADY에서는 무엇을 할 수 있나요?",
                "청약홈·국토교통부 공개 데이터를 매일 모아 청약을 고민할 때 필요한 정보를 한 화면에 정리했어요.\n"
                + "지역·단지명으로 찾기: 서울·강남·과천·분당처럼 지역이나 생활권, 단지 이름으로 공고를 찾아요. [생활권별 청약](/areas)\n"
                + "한 화면에서 판단: 일정·분양가·경쟁률·당첨가점·위치·주변 실거래를 공고 상세에서 함께 봐요. [이번 주 청약](/weekly)\n"
                + "내 가점과 비교: 날짜만 넣으면 가점을 계산하고 지역별 당첨가점과 비교해요. [가점 계산기](/score)\n"
                + "놓치지 않게: 관심 공고 저장, 캘린더 구독, 접수·발표 전날 알림을 받아요. [나의 청약](/my)"),
            new Faq("eligibility", "'내게 맞는 청약'의 판정은 정확한가요?",
                "사는 곳·무주택·세대주·통장 기간·가족 조건으로 공통 요건을 단순화해 본 1차 판정이에요. "
                + "소득·자산, 예치금, 거주 기간, 재당첨 제한처럼 공고마다 다르거나 묻지 않은 조건은 '확인할 것'으로 알려 드려요. "
                + "최종 자격은 모집공고와 청약홈에서 확인하세요. [자격 진단 해 보기](/match)"),
            new Faq("privacy", "가점·자격 진단에 입력한 내용은 저장되나요?",
                "입력한 내용은 지금 쓰는 브라우저에만 저장되고 서버로 보내지 않아요. 다른 기기에서는 다시 입력해야 하며, 화면의 '진단 기록 지우기'나 가점 계산기의 '다시 계산'으로 지울 수 있어요."),
            new Faq("alerts", "새 공고나 일정 알림은 어떻게 받나요?",
                "공고를 관심 공고로 저장한 뒤 '알림 켜기'를 누르면 접수·발표·계약 전날 알림을 받아요. 생활권이나 줍줍 새 공고 알림도 고를 수 있고, 하루 한 번 저녁에 모아서 보내요. "
                + "아이폰은 iOS 16.4 이상에서 TOADY를 홈 화면에 추가해 열어야 알림을 받을 수 있어요."),
            new Faq("data", "공고 정보는 어디서 가져오고 언제 바뀌나요?",
                "청약홈 공개 데이터를 매일 새벽 모아 반영하고, 동네 실거래는 국토교통부 실거래가 공개 자료를 써요. "
                + "공고가 정정되면 반영까지 시간이 걸릴 수 있으니 신청 전에는 공식 모집공고를 꼭 확인하세요.")
        ))
    );

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(((?:/[a-z0-9/_-]*)|(?:https://www\\.applyhome\\.co\\.kr))\\)");
    private final String domain;
    private final ObjectMapper json;

    public PublicFaqPage(@Value("${toady.web.domain}") String domain, ObjectMapper json) {
        this.domain = domain.replaceAll("/+$", "");
        this.json = json;
    }

    @GetMapping(value = "/api/v1/public/faq", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> faq() { return PublicHtml.cached("faq", this::render); }

    /** 앱의 자주 묻는 질문 화면용. 답의 [문구](경로) 표기는 앱이 링크로 바꾼다. */
    @GetMapping("/api/v1/public/faq-items")
    public ResponseEntity<List<Group>> items() {
        return PublicHtml.publicJson(GROUPS, Duration.ofHours(1));
    }

    /** 답을 문단(줄바꿈 기준)별 &lt;p&gt;로 만든다. */
    String answerHtml(String answer) {
        StringBuilder out = new StringBuilder();
        for (String line : answer.split("\n")) out.append("<p>").append(lineHtml(line.strip())).append("</p>");
        return out.toString();
    }
    /** 한 문단을 HTML로: 본문은 이스케이프하고, [문구](경로)만 링크로 바꾼다. 앱 화면 링크에는 유입처를 넘긴다(data-app). */
    String lineHtml(String answer) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = LINK.matcher(answer);
        int last = 0;
        while (matcher.find()) {
            out.append(esc(answer.substring(last, matcher.start())));
            String target = matcher.group(2), href = target.startsWith("/") ? domain + target : target;
            out.append("<a ").append(target.startsWith("/") ? "data-app " : "target='_blank' rel='noopener noreferrer' ")
                .append("href='").append(esc(href)).append("'>").append(esc(matcher.group(1))).append("</a>");
            last = matcher.end();
        }
        return out.append(esc(answer.substring(last))).toString();
    }
    /** 구조화 데이터에는 링크 표기 없이 문장만 넣는다. */
    static String plain(String answer) { return LINK.matcher(answer).replaceAll("$1").replace("\n", " "); }

    String jsonLd() {
        List<Map<String, Object>> entities = new ArrayList<>();
        for (Group group : GROUPS) for (Faq faq : group.items()) {
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("@type", "Answer"); answer.put("text", plain(faq.answer()));
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("@type", "Question"); question.put("name", faq.question()); question.put("acceptedAnswer", answer);
            entities.add(question);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org"); root.put("@type", "FAQPage"); root.put("mainEntity", entities);
        try {
            // </script> 조기 종료를 막는다.
            return "<script type='application/ld+json'>" + json.writeValueAsString(root).replace("</", "<\\/") + "</script>";
        } catch (JsonProcessingException failed) { throw new IllegalStateException(failed); }
    }

    private ResponseEntity<String> render() {
        int count = GROUPS.stream().mapToInt(group -> group.items().size()).sum();
        StringBuilder body = new StringBuilder("<nav aria-label='현재 위치'><a href='").append(esc(domain)).append("/'>TOADY</a><span>›</span><a data-app href='").append(esc(domain + "/faq")).append("'>자주 묻는 질문</a></nav>")
            .append("<section class='card'><span class='chip'>청약 자주 묻는 질문 ").append(count).append("개</span><h1>청약, 이것부터 확인하세요</h1>")
            .append("<p class='muted'>1순위 조건, 예치금, 특별공급, 무순위·줍줍, 당첨 후 자금까지 청약을 준비할 때 가장 많이 묻는 질문을 모았어요. 제도는 바뀔 수 있으니 신청 전에는 공식 모집공고를 함께 확인하세요.</p>")
            .append("<div class='chips faq-toc'>");
        for (Group group : GROUPS) body.append("<a class='chip' href='#").append(group.id()).append("'>").append(esc(group.title())).append("</a>");
        body.append("</div><div class='row'><a class='cta' data-app href='").append(esc(domain + "/match")).append("'>내가 넣을 수 있는 청약 찾기</a>")
            .append("<a class='cta secondary' data-app href='").append(esc(domain + "/score")).append("'>내 가점 계산하기</a></div></section>");
        for (Group group : GROUPS) {
            body.append("<section class='card faq' id='").append(group.id()).append("' aria-labelledby='").append(group.id()).append("-title'><h2 id='")
                .append(group.id()).append("-title'>").append(esc(group.title())).append("</h2>");
            for (Faq faq : group.items())
                body.append("<details id='").append(faq.id()).append("'><summary>").append(esc(faq.question())).append("</summary><div class='faq-a'>")
                    .append(answerHtml(faq.answer())).append("</div></details>");
            body.append("</section>");
        }
        body.append(PublicHtml.explore(domain, "/help/faq"))
            .append("<small>이 페이지는 이해를 돕기 위한 안내이며 법령·공고 해석의 기준이 아니에요. 문의: dukkeoba@gmail.com</small>");
        String head = STYLE + jsonLd();
        return PublicHtml.page(domain, "/help/faq", "청약 자주 묻는 질문 · 1순위·예치금·특별공급·무순위 · TOADY",
            "청약 1순위 조건, 지역별 예치금, 세대주 요건, 신혼·생애최초·다자녀·신생아 특별공급, 무순위·줍줍 자격, 계약금·중도금·취득세까지 자주 묻는 질문을 정리했어요.",
            domain + "/help/faq", body.toString(), head);
    }

    /** 질문을 펼치는 형태. 주소의 #질문으로 들어오면 그 질문을 열어 보여준다. */
    static final String STYLE = """
        <style>
        .faq h2{margin-bottom:4px}.faq details{border-bottom:1px solid #f1f5f9}.faq details:last-child{border-bottom:0}
        .faq summary{cursor:pointer;list-style:none;display:flex;justify-content:space-between;gap:12px;padding:14px 2px;font-weight:700;font-size:15px;color:#0f172a}
        .faq summary::-webkit-details-marker{display:none}.faq summary::after{content:'+';color:#94a3b8;font-weight:500;font-size:20px;line-height:1}
        .faq details[open] summary::after{content:'−'}.faq details,.faq{scroll-margin-top:72px}@media (max-width:560px){.faq details,.faq{scroll-margin-top:120px}}.faq-a{padding:0 2px 14px}.faq-a p{margin:0 0 6px;color:#334155;font-size:14px;line-height:1.75}.faq-a p:last-child{margin:0}
        .faq details:target summary,.faq details[open] summary{color:#1d4ed8}.faq-toc{margin:12px 0 14px}
        </style>
        <script>addEventListener('DOMContentLoaded',function(){var id=location.hash.slice(1);if(!/^[a-z0-9-]+$/.test(id))return;var el=document.getElementById(id);if(el&&el.tagName==='DETAILS'){el.open=true;el.scrollIntoView();}});</script>""";
}
