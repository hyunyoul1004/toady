package com.toady.web.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalEnvironmentTest {

	@TempDir
	Path tempDir;

	@Test
	void readsQuotedValuesWithoutOverwritingExistingProperties() throws IOException {
		// 따옴표 안의 특수문자를 보존하고, 외부에서 이미 설정한 시스템 속성을 우선한다.
		Path envFile = tempDir.resolve(".env.local");
		Files.writeString(envFile, """
				TOADY_TEST_DB_URL="jdbc:mariadb://localhost/toady"
				TOADY_TEST_DB_PASSWORD="contains#special!chars"
				""");

		String existingUrl = System.getProperty("TOADY_TEST_DB_URL");
		String existingPassword = System.getProperty("TOADY_TEST_DB_PASSWORD");
		try {
			System.clearProperty("TOADY_TEST_DB_URL");
			System.clearProperty("TOADY_TEST_DB_PASSWORD");
			LocalEnvironment.loadMissingProperties(envFile);

			assertThat(System.getProperty("TOADY_TEST_DB_URL")).isEqualTo("jdbc:mariadb://localhost/toady");
			assertThat(System.getProperty("TOADY_TEST_DB_PASSWORD")).isEqualTo("contains#special!chars");

			System.setProperty("TOADY_TEST_DB_URL", "provided-externally");
			LocalEnvironment.loadMissingProperties(envFile);
			assertThat(System.getProperty("TOADY_TEST_DB_URL")).isEqualTo("provided-externally");
		} finally {
			// 테스트가 변경한 전역 시스템 속성은 테스트 전 상태로 복구한다.
			restoreProperty("TOADY_TEST_DB_URL", existingUrl);
			restoreProperty("TOADY_TEST_DB_PASSWORD", existingPassword);
		}
	}

	private void restoreProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}
}
