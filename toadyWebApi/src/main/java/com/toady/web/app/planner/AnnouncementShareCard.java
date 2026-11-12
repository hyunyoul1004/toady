package com.toady.web.app.planner;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/** 크롤러가 JavaScript 없이 읽을 수 있는 공고별 PNG 카드. 공개된 공고 정보만 사용한다. */
final class AnnouncementShareCard {
    private AnnouncementShareCard() { }
    private static final String FONT_FAMILY = fontFamily();
    private static String fontFamily() {
        var families = java.util.Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(java.util.Locale.ENGLISH));
        return java.util.stream.Stream.of("Noto Sans CJK KR", "Noto Sans CJK JP", "Apple SD Gothic Neo", "Malgun Gothic", "SansSerif")
                .filter(families::contains).findFirst().orElse("SansSerif");
    }
    static byte[] png(PlannerSummary.Summary summary,String section,ShareScores.Range scores) throws java.io.IOException {
        BufferedImage image=new BufferedImage(1200,630,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(18,38,59));g.fillRect(0,0,1200,630);
            g.setColor(new Color(50,213,174));g.fillRoundRect(60,52,120,44,14,14);
            font(g,23,true);g.setColor(new Color(18,38,59));g.drawString("TOADY",75,83);
            g.setColor(new Color(173,196,217));font(g,24,false);g.drawString(PublicAnnouncementPage.label(section),205,83);
            g.setColor(Color.WHITE);font(g,50,true);
            String title=summary.houseNm();int split=fit(g,title,1060);
            g.drawString(title.substring(0,split),60,182);
            if(split<title.length()) g.drawString(ellipsis(g,title.substring(split),1060),60,247);
            g.setColor(new Color(173,196,217));font(g,25,false);g.drawString(ellipsis(g,summary.address(),1060),60,302);
            g.setColor(new Color(30,55,79));g.fillRoundRect(60,343,1080,157,24,24);
            g.setColor(new Color(173,196,217));font(g,23,false);
            g.drawString(scores.available()?"공개 최저 당첨가점":"청약 접수",88,386);g.drawString(scores.available()?"공개 최고 당첨가점":"당첨자 발표",670,386);
            g.setColor(Color.WHITE);font(g,31,true);
            g.drawString(scores.available()?(scores.minimum().isEmpty()?"미공개":scores.minimum()+"점"):date(summary.startDate())+" ~ "+date(summary.endDate()),88,446);
            g.drawString(scores.available()?(scores.maximum().isEmpty()?"미공개":scores.maximum()+"점"):date(summary.winnerDate()),670,446);
            g.setColor(new Color(50,213,174));font(g,27,true);
            g.drawString(scores.available()?"공개 주택형·지역 전체 범위 · 주택형별 가점은 상세에서":switch(section){case "rate" -> "주택형별 경쟁률을 한눈에 확인하세요";case "map" -> "공고 위치와 동네 실거래를 확인하세요";case "comment" -> "이 공고에 대한 이야기를 나눠보세요";default -> "청약 일정부터 분양가·입지까지 한곳에서";},60,558);
            g.setColor(new Color(173,196,217));font(g,18,false);g.drawString("청약홈 공개 자료 기준 · 최종 조건은 모집공고 확인",60,603);
        } finally {g.dispose();}
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    private static void font(Graphics2D g,int size,boolean bold) {g.setFont(new Font(FONT_FAMILY,bold?Font.BOLD:Font.PLAIN,size));}
    private static String date(Object date) {return date==null?"미제공":date.toString();}
    private static int fit(Graphics2D g,String text,int width) {int i=0;while(i<text.length()){int n=i+Character.charCount(text.codePointAt(i));if(g.getFontMetrics().stringWidth(text.substring(0,n))>width)break;i=n;}return i;}
    private static String ellipsis(Graphics2D g,String text,int width) {return g.getFontMetrics().stringWidth(text)<=width?text:text.substring(0,fit(g,text,width-g.getFontMetrics().stringWidth("…")))+"…";}
}
