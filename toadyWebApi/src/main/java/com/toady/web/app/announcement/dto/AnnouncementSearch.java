package com.toady.web.app.announcement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 공고 목록 조회에 사용하는 지역·키워드·기간 필터와 페이지 조건입니다. */
public class AnnouncementSearch {
    @Schema(description = "공급지역코드", example = "011")
    private String regionCode;

    @Schema(description = "주택명 검색어", example = "자이")
    @Size(max = 50)
    private String keyword;

    @Schema(description = "청약접수 시작일 조회 시작일(YYYY-MM-DD)", example = "2026-10-01")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Schema(description = "청약접수 시작일 조회 종료일(YYYY-MM-DD)", example = "2026-10-31")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Schema(description = "조회할 페이지(0부터 시작, 통합 목록은 (page+1)×size 최대 1000)", minimum = "0", example = "0")
    @Min(0)
    private int page;

    @Schema(description = "페이지당 조회 건수(1~100)", minimum = "1", maximum = "100", example = "20")
    @Min(1)
    @Max(100)
    private int size = 20;

    public String regionCode() {
        return regionCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String keyword() {
        return keyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int page() {
        return page;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int size() {
        return size;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
