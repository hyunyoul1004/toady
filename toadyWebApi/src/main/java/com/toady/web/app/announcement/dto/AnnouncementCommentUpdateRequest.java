package com.toady.web.app.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** 공고 댓글 수정 요청의 본문입니다. */
public record AnnouncementCommentUpdateRequest(
        @Schema(description = "수정할 댓글 내용(공백 불가, 최대 4,000자)", example = "수정한 댓글 내용입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 4000) String content
) {
}
