package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record ApplyhomeApiPage(List<JsonNode> data, int currentCount, long matchCount) {
}
