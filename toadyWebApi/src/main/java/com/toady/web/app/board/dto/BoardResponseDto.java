package com.toady.web.app.board.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * packageName    : com.toady.web.app.board.dto
 * fileName       : BoardResponseDto
 * author         : youn
 * date           : 2023/09/24
 * description    : 게시판 조회 Response DTO
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/09/24        youn       최초 생성
 */
public class BoardResponseDto {

    /** 목록 화면에 전달할 게시글 요약 정보입니다. */
    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Board{

        private Long boardSeq;

        private String boardType;

        private String boardLocation;

        private String boardTitle;

        private String boardContents;

        private Long fileSeq;

        private String boardTargetDateYn;

        private LocalDateTime boardStartDate;

        private LocalDateTime boardEndDate;

        private LocalDateTime createDt;

        private Long createUserSeq;

        private LocalDateTime updateDt;

        private Long updateUserSeq;
    }

}
