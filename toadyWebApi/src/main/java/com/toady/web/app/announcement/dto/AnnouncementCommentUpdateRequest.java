package com.toady.web.app.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공고 댓글 수정 요청의 본문입니다.
 * 컨트롤러가 @Valid @RequestBody로 받는다. @NotBlank: 공백만 있는 글 거부, @Size(max = 4000): 4,000자 제한.
 */
public record AnnouncementCommentUpdateRequest(
        @Schema(description = "수정할 댓글 내용(공백 불가, 최대 4,000자)", example = "수정한 댓글 내용입니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 4000) String content
) {
}
