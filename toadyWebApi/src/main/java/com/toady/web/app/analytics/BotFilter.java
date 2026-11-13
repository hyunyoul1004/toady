package com.toady.web.app.analytics;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * 검색 로봇·링크 미리보기·자동화 브라우저는 자바스크립트를 실행해 방문 기록을 보낼 수 있다.
 * 사람 방문 지표가 부풀지 않도록 이런 요청은 받되 저장하지 않는다(응답은 같게 204).
 */
final class BotFilter {
    private BotFilter() { }
    // 'bot'은 Googlebot/2.1·AhrefsBot; 처럼 뒤에 버전·구분자가 붙을 때만 본다(기기명 CUBOT_X30 같은 실제 휴대폰을 거르지 않게).
    // 다음은 수집 로봇 Daumoa만 거른다(Daum 앱 인앱 브라우저 DaumApps는 사람 방문이다).
    private static final Pattern BOT = Pattern.compile(
        "bot(?=[/;)\\-]|$)|crawl|spider|slurp|yeti|daumoa|bingpreview|headless|lighthouse|pagespeed|preview|facebookexternalhit|kakaotalk-scrap|kakaostory-og|whatsapp|telegrambot|slackbot|discordbot|curl|wget|python-requests|okhttp|java/",
        Pattern.CASE_INSENSITIVE);
    static boolean isBot(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        return agent == null || agent.isBlank() || BOT.matcher(agent).find();
    }
}
