package com.toady.web.core.controller;


import com.toady.web.core.pagination.PaginationInfo;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * 기본 컨트롤러
 *
 * @author hyunyoul
 */
public class BaseController {

    /**
     * Pagination Setup
     * Spring 페이지 결과를 화면용 PaginationInfo로 바꿔 응답 맵의 pagination 항목에 넣습니다.
     *
     * @param rtnMap
     * @param page
     * @param pageBlockSize
     * @param <T>
     */
    protected <T> void setPagination(Map<String, Object> rtnMap, Page<T> page, int pageBlockSize, int recordPerCount) {
        //@formatter:off
        PaginationInfo paginationInfo = new PaginationInfo(
            page.getNumber(),
            page.getNumberOfElements(),
            page.getTotalElements(),
			page.getTotalPages(),
            pageBlockSize,
            recordPerCount);
        //@formatter:on
        
        rtnMap.put("pagination", paginationInfo);
    }
}
