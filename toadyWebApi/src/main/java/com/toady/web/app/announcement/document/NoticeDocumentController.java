package com.toady.web.app.announcement.document;

import com.toady.web.app.planner.PlannerSummary;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/notice-documents")
public class NoticeDocumentController {
    private final NoticeDocumentService documents;
    private final PlannerSummary summaries;
    public NoticeDocumentController(NoticeDocumentService documents, PlannerSummary summaries) {
        this.documents = documents; this.summaries = summaries;
    }
    @GetMapping("/{type}/{house}/{notice}")
    public NoticeDocumentService.Result documents(
            @PathVariable @Pattern(regexp="apt|urban|remaining|public-rental|optional") String type,
            @PathVariable @Pattern(regexp="[0-9]{1,40}") String house,
            @PathVariable @Pattern(regexp="[0-9]{1,40}") String notice) {
        summaries.get(type, house, notice); // 저장된 공고만 조회해 임의 주소 수집 요청을 막는다.
        return documents.get(type, house, notice);
    }
}
