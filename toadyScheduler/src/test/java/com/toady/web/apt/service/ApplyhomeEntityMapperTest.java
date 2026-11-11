package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.apt.entity.AptLttotPblancDetail;
import com.toady.web.apt.entity.UrbtyOfctlLttotPblancMdl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyhomeEntityMapperTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ApplyhomeEntityMapper mapper = new ApplyhomeEntityMapper();

	@Test
	void mapsUppercaseApiFieldsAndNumericIdentifiers() throws Exception {
		// API의 대문자 필드명과 숫자 값도 엔티티의 문자열 필드에 맞게 변환되어야 한다.
		JsonNode response = objectMapper.readTree("""
				{
				  "HOUSE_MANAGE_NO": 2022000248,
				  "PBLANC_NO": "2022000248",
				  "HOUSE_NM": "창동 다우아트리체",
				  "TOT_SUPLY_HSHLDCO": 89
				}
				""");

		AptLttotPblancDetail entity = mapper.toEntity(response, AptLttotPblancDetail.class);

		assertThat(entity.getHouseManageNo()).isEqualTo("2022000248");
		assertThat(entity.getPblancNo()).isEqualTo("2022000248");
		assertThat(entity.getHouseNm()).isEqualTo("창동 다우아트리체");
		assertThat(entity.getTotSuplyHshldco()).isEqualTo("89");
	}

	@Test
	void mapsOptionalModelNumberAndRepeatedTypeFields() throws Exception {
		// 선택 필드가 null이어도 매핑이 실패하지 않고, 반복적으로 쓰이는 TP 필드도 보존한다.
		JsonNode response = objectMapper.readTree("""
				{
				  "HOUSE_MANAGE_NO": "2022950004",
				  "PBLANC_NO": "2022950004",
				  "MODEL_NO": null,
				  "TP": "48A1",
				  "SUPLY_HSHLDCO": 24
				}
				""");

		UrbtyOfctlLttotPblancMdl entity = mapper.toEntity(response, UrbtyOfctlLttotPblancMdl.class);

		assertThat(entity.getId()).isNull();
		assertThat(entity.getHouseManageNo()).isEqualTo("2022950004");
		assertThat(entity.getModelNo()).isNull();
		assertThat(entity.getTp()).isEqualTo("48A1");
		assertThat(entity.getSuplyHshldco()).isEqualTo("24");
	}

}
