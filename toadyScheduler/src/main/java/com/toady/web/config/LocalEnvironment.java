package com.toady.web.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntelliJ의 직접 실행에도 git에서 제외한 로컬 환경설정을 전달한다.
 * MainApplication.main이 Spring을 띄우기 전에 부른다. 읽은 값은 System 속성이 되어 application.yml의 ${DB_URL} 같은 자리에 들어간다.
 * 운영(docker)에서는 .env.local이 없으므로 아무 일도 하지 않는다.
 */
public final class LocalEnvironment {

	private static final Pattern ENTRY = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$");
	private static final Path LOCAL_ENV_FILE = Path.of(".env.local");

	private LocalEnvironment() {
	}

	/**
	 * 외부에 설정된 값은 유지하고, 누락된 값만 프로젝트 루트의 .env.local에서 읽는다.
	 */
	public static void loadMissingProperties() {
		loadMissingProperties(LOCAL_ENV_FILE);
	}

	/** 파일 경로를 받는 버전(테스트용). KEY=VALUE 줄만 읽고, 빈 줄·# 주석·형식이 다른 줄은 건너뛴다. */
	static void loadMissingProperties(Path envFile) {
		if (!Files.isRegularFile(envFile)) {
			return;
		}

		try {
			for (String line : Files.readAllLines(envFile)) {
				String trimmedLine = line.trim();
				if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
					continue;
				}

				Matcher matcher = ENTRY.matcher(trimmedLine);
				if (!matcher.matches()) {
					continue;
				}

				String key = matcher.group(1);
				if (System.getenv(key) != null || System.getProperty(key) != null) {
					continue;
				}

				System.setProperty(key, unquote(matcher.group(2).trim()));
			}
		} catch (IOException exception) {
			throw new IllegalStateException("Could not read local environment configuration", exception);
		}
	}

	/** 값 양끝의 큰따옴표·작은따옴표를 벗긴다. */
	private static String unquote(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}
}
