package com.toady.web.apt.service;

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

	private void release(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
			statement.setString(1, LOCK_NAME);
			statement.executeQuery().close();
		}
	}
}
