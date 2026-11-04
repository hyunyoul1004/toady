package com.toady.web.app.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 여러 공고 유형의 결과를 통합 목록에서 공통으로 표현합니다. */
@Schema(description = "전체 공고 통합 목록 항목")
public record AnnouncementListItem(
        @Schema(description = "공고 유형", example = "apt")
        String type,
        @Schema(description = "주택관리번호", example = "2025000123")
        String houseManageNo,
        @Schema(description = "공고번호", example = "2025000123")
        String pblancNo,
        @Schema(description = "주택명", example = "래미안 아파트")
        String houseNm,
        @Schema(description = "주택구분코드", example = "05")
        String houseSecd,
        @Schema(description = "주택구분코드명", example = "민영")
        String houseSecdNm,
        @Schema(description = "공급지역코드", example = "011")
        String regionCode,
        @Schema(description = "공급지역명", example = "서울특별시")
        String regionName,
        @Schema(description = "공급위치 주소")
        String hssplyAdres,
        @Schema(description = "공급위치 우편번호", example = "12345")
        String hssplyZip,
        @Schema(description = "청약접수 시작일", example = "2026-10-01")
        String rceptBgnde,
        @Schema(description = "청약접수 종료일", example = "2026-10-10")
        String rceptEndde,
        @Schema(description = "1순위 해당지역 접수시작일")
        String gnrlRnk1CrspareaRcptde,
        @Schema(description = "1순위 해당지역 접수종료일")
        String gnrlRnk1CrspareaEndde,
        @Schema(description = "1순위 경기지역 접수시작일")
        String gnrlRnk1EtcGgRcptde,
        @Schema(description = "1순위 경기지역 접수종료일")
        String gnrlRnk1EtcGgEndde,
        @Schema(description = "1순위 기타지역 접수시작일")
        String gnrlRnk1EtcAreaRcptde,
        @Schema(description = "1순위 기타지역 접수종료일")
        String gnrlRnk1EtcAreaEndde,
        @Schema(description = "2순위 해당지역 접수시작일")
        String gnrlRnk2CrspareaRcptde,
        @Schema(description = "2순위 해당지역 접수종료일")
        String gnrlRnk2CrspareaEndde,
        @Schema(description = "2순위 경기지역 접수시작일")
        String gnrlRnk2EtcGgRcptde,
        @Schema(description = "2순위 경기지역 접수종료일")
        String gnrlRnk2EtcGgEndde,
        @Schema(description = "2순위 기타지역 접수시작일")
        String gnrlRnk2EtcAreaRcptde,
        @Schema(description = "2순위 기타지역 접수종료일")
        String gnrlRnk2EtcAreaEndde,
        @Schema(description = "공급규모")
        String totalSupplyHouseholds
) {
}
