package com.toady.web.app.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 공고 댓글과 작성자 표시명, 생성·수정 정보를 담는 응답 모델입니다. */
@Schema(description = "공고 댓글 정보")
public record AnnouncementCommentResponse(
        @Schema(description = "댓글 번호", example = "1")
        Long commentSeq,
        @Schema(description = "공고 유형", example = "apt")
        String announcementType,
        @Schema(description = "주택관리번호", example = "2025000123")
        String houseManageNo,
        @Schema(description = "공고번호", example = "2025000123")
        String pblancNo,
        @Schema(description = "부모 댓글 번호. 답글이 아니면 null", example = "null")
        Long parentCommentSeq,
        @Schema(description = "댓글 내용", example = "관심 있는 공고라서 정보 공유합니다.")
        String content,
        @Schema(description = "추천 수", example = "0")
        Integer recommend,
        @Schema(description = "비추천 수", example = "0")
        Integer oppose,
        @Schema(description = "삭제 여부", example = "N")
        String deleteYn,
        @Schema(description = "작성자 사용자 번호", example = "100")
        Long createUserSeq,
        @Schema(description = "작성자 닉네임", example = "toady")
        String userNicName,
        @Schema(description = "작성 일시", example = "2026-10-30T12:00:00")
        LocalDateTime regDt,
        @Schema(description = "수정자 사용자 번호", example = "100")
        Long updateUserSeq,
        @Schema(description = "수정 일시", example = "2026-10-30T12:30:00")
        LocalDateTime updateDt,
        @Schema(description = "작성자 프로필 캐릭터 키. 고르지 않았으면 null(닉네임 첫 글자로 표시)", example = "toad")
        String profileIcon
) {
    /** JPQL 생성자 표현식용(프로필 캐릭터는 TB_USER_PROFILE에서 따로 채운다). */
    public AnnouncementCommentResponse(Long commentSeq, String announcementType, String houseManageNo, String pblancNo, Long parentCommentSeq,
            String content, Integer recommend, Integer oppose, String deleteYn, Long createUserSeq, String userNicName,
            LocalDateTime regDt, Long updateUserSeq, LocalDateTime updateDt) {
        this(commentSeq, announcementType, houseManageNo, pblancNo, parentCommentSeq, content, recommend, oppose, deleteYn,
                createUserSeq, userNicName, regDt, updateUserSeq, updateDt, null);
    }

    public AnnouncementCommentResponse withProfileIcon(String icon) {
        return new AnnouncementCommentResponse(commentSeq, announcementType, houseManageNo, pblancNo, parentCommentSeq, content, recommend,
                oppose, deleteYn, createUserSeq, userNicName, regDt, updateUserSeq, updateDt, icon);
    }
}
