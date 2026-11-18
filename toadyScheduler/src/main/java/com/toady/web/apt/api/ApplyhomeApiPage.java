package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 청약홈(공공데이터포털 odcloud) API 한 페이지의 응답.
 * data: 이 페이지의 행, currentCount: 이 페이지 행 수, matchCount: 조건에 맞는 전체 행 수.
 * ApplyhomeApiClient·ApplyhomeCompetitionApiClient가 만들고 ApplyhomePageCollector가 페이지를 이어 붙일 때 쓴다.
 */
public record ApplyhomeApiPage(List<JsonNode> data, int currentCount, long matchCount) {
}
