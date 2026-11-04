package com.toady.web.core.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * packageName    : com.toady.web.config
 * fileName       : BoardLocation
 * author         : youn
 * date           : 3/9/24
 * description    : 게시판 지역 구분 ( TB_BOARD.BOARD_LOCATION )
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 3/9/24        youn       최초 생성
 */
@Getter
@AllArgsConstructor // 모든 필드 값을 파라미터로 받는 생성자를 생성
public enum BoardLocation {

    ALL("ALL", "전체 조회"),
    SEOUL("SEOUL", "서울"),
    PAJUSI("PAJU", "파주시")
    ;

    private String code;
    private String description;
}
