package com.toady.web.app.community;

import java.util.regex.Pattern;

/**
 * 쪽지 내용 규칙. 첨부 없이 글자만 받는다.
 * - 상대가 아직 답하지 않은 첫 쪽지에는 링크·전화번호·오픈채팅·계좌번호처럼 밖으로 데려가는 내용을 막는다(홍보·사기 유도 차단).
 * - 청약통장 매매·분양권 전매 알선처럼 불법 거래가 의심되는 문구는 막지 않고 받는 사람에게 경고를 붙인다(일상 대화에도 나오는 말이라).
 */
public final class DmRules {
    private DmRules() { }

    public static final int MAX_LENGTH = 1000;

    private static final Pattern LINK = Pattern.compile(
            "(https?://|www\\.|open\\.kakao|kakao\\.com|[a-z0-9-]+\\.(com|net|kr|co|me|io|ly|gg|xyz)(/|\\b))", Pattern.CASE_INSENSITIVE);
    /** 휴대폰 번호(구분자 허용)와 지역 번호(하이픈). 날짜(2026-11-16)와 겹치지 않게 앞자리 0으로 시작하는 것만 본다. */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(01[016789][\\s.-]?\\d{3,4}[\\s.-]?\\d{4}|0\\d{1,2}-\\d{3,4}-\\d{4})(?!\\d)");
    /** 계좌번호 후보: 숫자 묶음(하이픈 연결 가능)의 숫자가 10자리 이상. 날짜·금액은 10자리가 되지 않는다. */
    private static final Pattern NUMBER_RUN = Pattern.compile("\\d+(?:-\\d+){0,5}");
    /** 메신저로 데려가는 말. '온라인'·'텔레비전' 같은 낱말은 걸리지 않게 아이디·주소와 함께 쓸 때만 본다. */
    private static final Pattern MESSENGER = Pattern.compile("오픈\\s*채팅|((카톡|카카오톡|텔레그램|라인)\\s*(아이디|id|주소|방\\s*주소|추가|친추))", Pattern.CASE_INSENSITIVE);

    private static final Pattern TRADE = Pattern.compile(
            "(통장\\s*(매매|매입|삽니다|팝니다|구매|판매|양도|대여|빌려)"
            + "|청약\\s*통장.{0,10}(사|팔|양도|넘기|매매)"
            + "|(분양권|딱지|입주권)\\s*(전매|매매|삽니다|팝니다|매입)"
            + "|전매\\s*(가능|알선|도와|해\\s*드)"
            + "|(웃돈|프리미엄|피)\\s*(드립|드려|줍니다|받고|얹어)"
            + "|명의\\s*(대여|빌려|빌립)"
            + "|선입금|입금\\s*(먼저|부탁|하시면))");

    public enum Problem { EMPTY, TOO_LONG, CONTACT }

    /** 보내기 전에 막을 문제. 없으면 null. firstUnanswered: 상대가 아직 이 대화에서 답하지 않았을 때. */
    public static Problem check(String content, boolean firstUnanswered) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) return Problem.EMPTY;
        if (text.length() > MAX_LENGTH) return Problem.TOO_LONG;
        if (firstUnanswered && hasContact(text)) return Problem.CONTACT;
        return null;
    }

    static boolean hasContact(String text) {
        String flat = text.replace(' ', ' ');
        if (LINK.matcher(flat).find() || PHONE.matcher(flat).find() || MESSENGER.matcher(flat).find()) return true;
        var runs = NUMBER_RUN.matcher(flat);
        while (runs.find()) if (runs.group().replace("-", "").length() >= 10) return true;
        return false;
    }

    /** 받는 사람에게 경고를 붙일 거래 의심 문구가 있는가. */
    public static boolean flagged(String content) {
        return content != null && TRADE.matcher(content.replaceAll("\\s+", " ")).find();
    }

    public static String message(Problem problem) {
        return switch (problem) {
            case EMPTY -> "보낼 내용을 입력해 주세요.";
            case TOO_LONG -> "쪽지는 " + MAX_LENGTH + "자까지 보낼 수 있어요.";
            case CONTACT -> "상대가 답하기 전 첫 쪽지에는 링크·전화번호·메신저 아이디·계좌번호를 넣을 수 없어요. 대화가 이어진 뒤에 나눠 주세요.";
        };
    }
}
