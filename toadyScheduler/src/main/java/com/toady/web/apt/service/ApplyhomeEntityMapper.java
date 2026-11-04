package com.toady.web.apt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/** 청약홈 JSON 응답을 JPA 엔티티로 변환한다. */
@Component
public class ApplyhomeEntityMapper {

	private final ObjectMapper objectMapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	public <T> T toEntity(JsonNode source, Class<T> entityType) {
		try {
			return objectMapper.treeToValue(source, entityType);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to map Applyhome response to " + entityType.getSimpleName(), exception);
		}
	}
}
