package com.toady.web.app.board.dto;

import com.toady.web.core.constants.CmnConst;
import lombok.*;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

/**
 * packageName    : com.toady.web.app.board.dto
 * fileName       : BoardRequestDto
 * author         : youn
 * date           : 2023/08/15
 * description    : 게시판 조회 Request DTO
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/08/15        youn       최초 생성
 */
public class BoardRequestDto {

    /** 게시판 종류·지역·제목과 페이지 크기를 전달하는 목록 검색 조건입니다. */
    @Setter
    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Search{

        private String boardLocation;

        @Builder.Default
        private String boardTitle = "";

        private String boardType;

        @NotNull
        @Min(1)
        private int pageNumber;

        @Getter
        @NotNull
        @Min(1)
        @Max(100)
        @Builder.Default
        private int recordPerCount = CmnConst.RECORD_PER_COUNT;

        public int getPageNumber() {
            return pageNumber-1;
        }

    }
}
