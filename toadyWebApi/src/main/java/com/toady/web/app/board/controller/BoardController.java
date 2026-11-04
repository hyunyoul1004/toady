package com.toady.web.app.board.controller;

import com.google.common.base.Enums;
import com.toady.web.app.board.dto.BoardRequestDto;
import com.toady.web.app.board.dto.BoardResponseDto;
import com.toady.web.app.board.entity.BoardEntity;
import com.toady.web.app.board.service.BoardService;
import com.toady.web.core.constants.BoardLocation;
import com.toady.web.core.constants.BoardType;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.controller.BaseController;
import com.toady.web.core.response.ResData;
import com.toady.web.core.util.CmnUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * packageName    : com.toady.web.app.board.controller
 * fileName       : BoardController
 * author         : youn
 * date           : 2023/08/06
 * description    : 게시판 컨트롤러.
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/08/06        youn       최초 생성
 */
@Slf4j
@RestController
@RequiredArgsConstructor // 자동주입
@RequestMapping("/webApi/boards")
public class BoardController extends BaseController {

    private final BoardService boardService;

    @Operation(summary = "게시판 목록 조회", tags = "게시판 API")
    @GetMapping("/board-list")
    public ResponseEntity<ResData> getBoardList(@Valid BoardRequestDto.Search search, BindingResult result){

        if(!Enums.getIfPresent(BoardType.class, search.getBoardType()).isPresent() ||
           !Enums.getIfPresent(BoardLocation.class, search.getBoardLocation()).isPresent()){
            //Enum에 정의된 boardType 또는 BoardLocation이 없으면.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.VALID_ERROR.getCode(), ResultCode.VALID_ERROR.getMessage()));
        }

        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResData.of(ResultCode.VALID_ERROR.getCode(), CmnUtils.getErrMsg(result, '\n')));
        }

        Map<String, Object> rtnMap = new HashMap<>();

        //regDt를 기준으로 DESC 정렬
        Sort sort = Sort.by(Sort.Direction.DESC, "createDt");

        //10건씩 조회
        Pageable pageable = PageRequest.of(search.getPageNumber(), search.getRecordPerCount(), sort);

        Page<BoardEntity> resultList = boardService.getBoardList(search, pageable);

        //resultList를 담아서 넘김.
        rtnMap.put("resultList", resultList);

        //10개씩 5페이지 표시.
        setPagination(rtnMap, resultList, 5, search.getRecordPerCount());

        return ResponseEntity.ok().body(ResData.of(rtnMap, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

}
