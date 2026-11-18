package com.toady.web.app.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 공고 댓글과 작성자 표시명, 생성·수정 정보를 담는 응답 모델입니다.
 * 목록은 AnnouncementCommentRepository의 JPQL(select new …)이 아래 14개 인자 생성자로 바로 만들고,
 * AnnouncementCommentService가 프로필 캐릭터·운영팀 표시를 with… 메서드로 덧붙인다.
 * record는 값을 바꿀 수 없어서, 값을 더할 때는 새 객체를 만들어 돌려준다.
 */
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
        String profileIcon,
        @Schema(description = "운영팀이 쓴 글이면 true(관리자 사이트에서 운영팀 계정으로 작성)", example = "false")
        boolean official,
        @Schema(description = "운영팀이 공고 이야기 맨 위에 고정한 원글이면 true", example = "false")
        boolean pinned
) {
    /** JPQL 생성자 표현식용(프로필 캐릭터는 TB_USER_PROFILE에서 따로 채운다). 나머지 세 값은 null·false로 시작한다. */
    public AnnouncementCommentResponse(Long commentSeq, String announcementType, String houseManageNo, String pblancNo, Long parentCommentSeq,
            String content, Integer recommend, Integer oppose, String deleteYn, Long createUserSeq, String userNicName,
            LocalDateTime regDt, Long updateUserSeq, LocalDateTime updateDt) {
        this(commentSeq, announcementType, houseManageNo, pblancNo, parentCommentSeq, content, recommend, oppose, deleteYn,
                createUserSeq, userNicName, regDt, updateUserSeq, updateDt, null, false, false);
    }

    /** 프로필 캐릭터 키만 바꾼 새 객체를 돌려준다. */
    public AnnouncementCommentResponse withProfileIcon(String icon) {
        return new AnnouncementCommentResponse(commentSeq, announcementType, houseManageNo, pblancNo, parentCommentSeq, content, recommend,
                oppose, deleteYn, createUserSeq, userNicName, regDt, updateUserSeq, updateDt, icon, official, pinned);
    }

    /** 운영팀 글 여부를 붙인다. 고정(pinned)은 운영팀이 쓴 원글(답글 아님)일 때만 true가 된다. */
    public AnnouncementCommentResponse withOfficial(boolean value, boolean pin) {
        return new AnnouncementCommentResponse(commentSeq, announcementType, houseManageNo, pblancNo, parentCommentSeq, content, recommend,
                oppose, deleteYn, createUserSeq, userNicName, regDt, updateUserSeq, updateDt, profileIcon, value, value && pin && parentCommentSeq == null);
    }
}
