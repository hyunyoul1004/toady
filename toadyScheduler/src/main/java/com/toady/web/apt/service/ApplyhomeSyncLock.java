package com.toady.web.apt.service;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MariaDB advisory lock으로 여러 스케줄러 인스턴스의 동시 적재를 차단한다.
 */
@Component
public class ApplyhomeSyncLock {

	private static final Logger log = LoggerFactory.getLogger(ApplyhomeSyncLock.class);
	private static final String LOCK_NAME = "toady.applyhome.sync";

	private final DataSource dataSource;

	public ApplyhomeSyncLock(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void execute(Runnable action) {
		try (Connection connection = dataSource.getConnection()) {
			if (!acquire(connection)) {
				throw new ApplyhomeSyncBusyException();
			}
			try {
				action.run();
			} finally {
				release(connection);
			}
		} catch (SQLException exception) {
			throw new IllegalStateException("Could not manage the Applyhome synchronization lock", exception);
		}
	}

	private boolean acquire(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
			statement.setString(1, LOCK_NAME);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() && resultSet.getInt(1) == 1;
			}
		}
	}

	/**
	 * 잠금을 푼다. 해제가 실패해도 수집 결과(일부 실패·대기 등)를 덮지 않도록 예외를 던지지 않고,
	 * 잠금을 쥔 채 풀로 돌아가 이후 수집이 계속 '대기'가 되지 않게 그 연결은 풀에서 폐기한다.
	 */
	private void release(Connection connection) {
		try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
			statement.setString(1, LOCK_NAME);
			statement.executeQuery().close();
		} catch (SQLException exception) {
			log.warn("Applyhome sync lock release failed; evicting the connection: {}", exception.getMessage());
			try {
				if (dataSource.isWrapperFor(HikariDataSource.class)) dataSource.unwrap(HikariDataSource.class).evictConnection(connection);
				else connection.abort(Runnable::run);
			} catch (SQLException ignored) { /* 연결이 이미 끊겼으면 잠금도 함께 풀린다 */ }
		}
	}
}
