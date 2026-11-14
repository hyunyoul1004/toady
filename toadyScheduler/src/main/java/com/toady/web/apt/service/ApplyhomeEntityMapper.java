package com.toady.web.apt.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 청약홈 JSON 응답을 JPA 엔티티로 변환한다. */
@Component
public class ApplyhomeEntityMapper {

	private static final Logger LOG = LoggerFactory.getLogger(ApplyhomeEntityMapper.class);
	/** 이미 알린 '엔티티에 없는 응답 필드'(엔티티.필드). 프로세스마다 한 번만 남긴다. */
	private final Set<String> reportedUnknown = ConcurrentHashMap.newKeySet();

	/**
	 * 응답에는 있는데 엔티티에 매핑되지 않은 필드를 한 번씩 경고로 남긴다. 엔티티 필드명(=컬럼명)이 청약홈 키와 다르면
	 * 값이 조용히 null로 저장되므로(예: 공급위치 HSSPLY_ADRES), 이름이 어긋난 필드를 로그에서 바로 찾게 한다.
	 */
	private final ObjectMapper objectMapper = JsonMapper.builder()
			.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
			.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.addHandler(new DeserializationProblemHandler() {
				@Override
				public boolean handleUnknownProperty(DeserializationContext context, JsonParser parser,
						JsonDeserializer<?> deserializer, Object bean, String property) throws IOException {
					String type = bean == null ? "?" : bean.getClass().getSimpleName();
					if (reportedUnknown.size() < 2000 && reportedUnknown.add(type + "." + property))
						LOG.warn("청약홈 응답 필드가 엔티티에 매핑되지 않음: {}.{} (필드명 불일치면 값이 저장되지 않습니다)", type, property);
					parser.skipChildren();
					return true;
				}
			})
			.build();

	/** 테스트·점검용: 지금까지 알린 매핑되지 않은 필드. */
	Set<String> unmappedFields() { return Set.copyOf(reportedUnknown); }

	public <T> T toEntity(JsonNode source, Class<T> entityType) {
		try {
			return objectMapper.treeToValue(source, entityType);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to map Applyhome response to " + entityType.getSimpleName(), exception);
		}
	}
}
