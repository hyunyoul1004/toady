package com.toady.web.app.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** 공고 댓글 작성에 필요한 내용과 선택적 부모 댓글 번호입니다. */
public record AnnouncementCommentRequest(
        @Schema(description = "답글의 부모 댓글 번호. 일반 댓글은 생략합니다.", example = "1")
        Long parentCommentSeq,
        @Schema(description = "댓글 내용(공백 불가, 최대 4,000자)", example = "관심 있는 공고라서 정보 공유합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 4000) String content
) {
}
