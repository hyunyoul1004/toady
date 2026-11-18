package com.toady.web.app.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 자동완성 검색 결과에 필요한 공고 정보를 표현합니다.
 * GET /api/v1/announcements/suggestions 응답 항목. AnnouncementSearchService.suggestions가 유형별 엔티티에서 만든다.
 * @Schema는 Swagger 문서에 보이는 설명과 예시 값이다.
 */
@Schema(description = "분양 공고 자동완성 검색 결과")
public record AnnouncementSuggestionResponse(
        @Schema(description = "공고 유형", example = "apt")
        String type,
        @Schema(description = "주택관리번호", example = "2026000123")
        String houseManageNo,
        @Schema(description = "공고번호", example = "2026000123")
        String pblancNo,
        @Schema(description = "주택명", example = "메이플자이")
        String houseNm,
        @Schema(description = "주택구분코드명", example = "민영")
        String houseSecdNm,
        @Schema(description = "공급지역명", example = "서울")
        String subscrptAreaCodeNm,
        @Schema(description = "공급위치 주소", example = "서울특별시 서초구 잠원동 60-3 일원")
        String hssplyAdres,
        @Schema(description = "청약접수 시작일", example = "2026-08-12")
        String rceptBgnde,
        @Schema(description = "청약접수 종료일", example = "2026-08-14")
        String rceptEndde
) {
}
