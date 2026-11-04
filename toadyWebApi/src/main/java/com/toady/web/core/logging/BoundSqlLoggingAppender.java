package com.toady.web.core.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hibernate SQL과 바인딩 값을 스레드별로 모아 파라미터가 반영된 쿼리를 별도 로그로 출력합니다. */
public class BoundSqlLoggingAppender extends AppenderBase<ILoggingEvent> {
    private static final String SQL_LOGGER = "org.hibernate.SQL";
    private static final String BIND_LOGGER = "org.hibernate.orm.jdbc.bind";
    private static final Logger SQL_LOG = LoggerFactory.getLogger("com.toady.sql.bound");
    private static final Pattern BINDING_PATTERN =
            Pattern.compile("binding parameter \\((\\d+):([A-Z_]+)\\) <- \\[(.*)]", Pattern.DOTALL);

    private final Map<String, PendingSql> pendingByThread = new ConcurrentHashMap<>();

    @Override
    protected void append(ILoggingEvent event) {
        String threadName = event.getThreadName();
        if (SQL_LOGGER.equals(event.getLoggerName())) {
            onSql(threadName, event.getFormattedMessage());
        } else if (BIND_LOGGER.equals(event.getLoggerName())) {
            onBinding(threadName, event.getFormattedMessage());
        }
    }

    private void onSql(String threadName, String sql) {
        PendingSql previous = pendingByThread.remove(threadName);
        if (previous != null) {
            SQL_LOG.warn("SQL did not receive all bind values ({}/{}):\n{}",
                    previous.values.size(), previous.parameterCount, previous.sql);
        }

        int parameterCount = countParameters(sql);
        if (parameterCount == 0) {
            SQL_LOG.info("\n{}", sql);
            return;
        }
        pendingByThread.put(threadName, new PendingSql(sql, parameterCount));
    }

    private void onBinding(String threadName, String message) {
        Matcher matcher = BINDING_PATTERN.matcher(message);
        if (!matcher.matches()) {
            SQL_LOG.warn("Could not parse Hibernate bind log: {}", message);
            return;
        }

        PendingSql pending = pendingByThread.get(threadName);
        if (pending == null) {
            return;
        }

        int position = Integer.parseInt(matcher.group(1));
        pending.values.put(position, new BoundValue(matcher.group(2), matcher.group(3)));
        if (pending.values.size() == pending.parameterCount) {
            pendingByThread.remove(threadName, pending);
            SQL_LOG.info("\n{}", interpolate(pending.sql, pending.values));
        }
    }

    /** 따옴표와 SQL 주석 안의 물음표를 제외하고 바인딩 자리표시자 수를 계산합니다. */
    static int countParameters(String sql) {
        int count = 0;
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                }
            } else if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
            } else if (quote != 0) {
                if (current == '\\') {
                    i++;
                } else if (current == quote) {
                    if (next == quote) {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '-' && next == '-') {
                lineComment = true;
                i++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                i++;
            } else if (current == '\'' || current == '"' || current == '`') {
                quote = current;
            } else if (current == '?') {
                count++;
            }
        }
        return count;
    }

    /** SQL 문자열·주석을 보존하면서 자리표시자만 해당 타입의 리터럴로 치환합니다. */
    static String interpolate(String sql, Map<Integer, BoundValue> values) {
        StringBuilder result = new StringBuilder(sql.length() + values.size() * 8);
        int parameter = 1;
        char quote = 0;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;

            if (lineComment) {
                result.append(current);
                if (current == '\n') {
                    lineComment = false;
                }
            } else if (blockComment) {
                result.append(current);
                if (current == '*' && next == '/') {
                    result.append(next);
                    blockComment = false;
                    i++;
                }
            } else if (quote != 0) {
                result.append(current);
                if (current == '\\' && i + 1 < sql.length()) {
                    result.append(sql.charAt(++i));
                } else if (current == quote) {
                    if (next == quote) {
                        result.append(next);
                        i++;
                    } else {
                        quote = 0;
                    }
                }
            } else if (current == '-' && next == '-') {
                result.append(current).append(next);
                lineComment = true;
                i++;
            } else if (current == '/' && next == '*') {
                result.append(current).append(next);
                blockComment = true;
                i++;
            } else if (current == '\'' || current == '"' || current == '`') {
                result.append(current);
                quote = current;
            } else if (current == '?') {
                BoundValue value = values.get(parameter++);
                if (value == null) {
                    throw new IllegalArgumentException("Missing SQL bind value for parameter " + (parameter - 1));
                }
                result.append(value.asSqlLiteral());
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    /** 로그에서 읽은 단일 바인딩의 JDBC 타입과 값을 보관합니다. */
    record BoundValue(String type, String value) {
        String asSqlLiteral() {
            if ("NULL".equalsIgnoreCase(value) || "null".equalsIgnoreCase(value)) {
                return "NULL";
            }
            if (type.contains("CHAR") || type.contains("STRING") || type.contains("DATE")
                    || type.contains("TIME") || type.contains("CLOB") || type.contains("UUID")
                    || type.contains("JSON") || type.contains("ENUM")) {
                return "'" + value.replace("'", "''") + "'";
            }
            return value;
        }
    }

    /** 한 스레드에서 바인딩 수신을 기다리는 SQL과 누적 파라미터를 보관합니다. */
    private record PendingSql(String sql, int parameterCount, Map<Integer, BoundValue> values) {
        private PendingSql(String sql, int parameterCount) {
            this(sql, parameterCount, new TreeMap<>());
        }
    }
}
