package com.toady.web.app.announcement.document;

import com.toady.web.app.planner.PlannerSummary;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 공고 모집공고문 원문(PDF 등) 링크를 돌려주는 API. GET /api/v1/notice-documents/{type}/{house}/{notice}
 * 흐름: 이 컨트롤러 → NoticeDocumentService → HttpNoticeDocumentTransport → 청약홈 상세 페이지.
 */
// @RestController: JSON을 돌려주는 컨트롤러. @Validated: 경로 변수의 @Pattern 검사를 켠다.
@RestController
@Validated
@RequestMapping("/api/v1/notice-documents")
public class NoticeDocumentController {
    private final NoticeDocumentService documents;
    private final PlannerSummary summaries;
    // 생성자 주입. 생성자가 하나뿐이면 스프링이 @Autowired 없이도 이 생성자로 빈을 넣는다.
    public NoticeDocumentController(NoticeDocumentService documents, PlannerSummary summaries) {
        this.documents = documents; this.summaries = summaries;
    }
    // @Pattern: 유형은 정해진 값만, 번호는 숫자만 받는다. 어기면 400.
    // 반환값 record(Result)는 그대로 JSON이 된다(ResData로 감싸지 않는다).
    // 다른 API와 달리 감싸지 않는 이유: 웹(toadyReactWeb NoticeDocuments.jsx)이 응답의 status·documents를 바로 읽는다.
    // ResData로 바꾸려면 웹의 읽는 쪽(data.data.status)을 같은 배포에서 함께 바꿔야 한다.
    @GetMapping("/{type}/{house}/{notice}")
    public NoticeDocumentService.Result documents(
            @PathVariable @Pattern(regexp="apt|urban|remaining|public-rental|optional") String type,
            @PathVariable @Pattern(regexp="[0-9]{1,40}") String house,
            @PathVariable @Pattern(regexp="[0-9]{1,40}") String notice) {
        summaries.get(type, house, notice); // 저장된 공고만 조회해 임의 주소 수집 요청을 막는다. 없는 공고면 여기서 404가 난다.
        return documents.get(type, house, notice);
    }
}
