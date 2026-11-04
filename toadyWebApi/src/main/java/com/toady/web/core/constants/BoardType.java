package com.toady.web.core.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * packageName    : com.toady.web.core.constants
 * fileName       : BoardType
 * author         : youn
 * date           : 3/11/24
 * description    : 게시판 지역 구분 ( TB_BOARD.BOARD_TYPE )
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 3/11/24        youn       최초 생성
 */
@Getter
@AllArgsConstructor // 모든 필드 값을 파라미터로 받는 생성자를 생성
public enum BoardType {

    NOTICE("NOTICE", "공지사항"),
    FREE("FREE", "자유게시판")
    ;

    private String code;
    private String description;

}
