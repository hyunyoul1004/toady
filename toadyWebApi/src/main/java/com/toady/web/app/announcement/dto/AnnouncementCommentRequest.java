package com.toady.web.app.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공고 댓글 작성에 필요한 내용과 선택적 부모 댓글 번호입니다.
 * 컨트롤러가 @Valid @RequestBody로 받아 AnnouncementCommentService.create에 넘긴다.
 * record: 필드·생성자·getter(parentCommentSeq())·equals를 자동으로 만드는 불변 데이터 클래스.
 * @Schema는 Swagger 문서 설명이고, @NotBlank·@Size는 @Valid 검증 규칙이다. 어기면 400 응답이 나간다.
 */
public record AnnouncementCommentRequest(
        @Schema(description = "답글의 부모 댓글 번호. 일반 댓글은 생략합니다.", example = "1")
        Long parentCommentSeq,
        @Schema(description = "댓글 내용(공백 불가, 최대 4,000자)", example = "관심 있는 공고라서 정보 공유합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 4000) String content
) {
}
