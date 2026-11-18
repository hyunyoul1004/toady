package com.toady.web.app.announcement.document;

import java.net.URI;

/**
 * 청약홈 공고 상세 HTML을 받아 오는 통로. NoticeDocumentService가 쓴다.
 * 인터페이스로 나눠 두어 테스트에서는 가짜 구현으로 바꿔 끼울 수 있다. 실제 구현은 HttpNoticeDocumentTransport.
 */
public interface NoticeDocumentTransport {
    /** 주소의 응답 본문을 바이트로 돌려준다. 실패하면 예외를 던진다. */
    byte[] get(URI uri) throws Exception;
}
