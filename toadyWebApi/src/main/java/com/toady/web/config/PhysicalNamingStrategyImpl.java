package com.toady.web.config;

import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import java.util.Locale;

/**
 * packageName    : com.toady.web.config
 * fileName       : PhysicalNamingStrategyImpl
 * author         : youn
 * date           : 2022/05/19
 * description    : CamelCase to Underscore., table, column 명을 전부 대문자로 변환.
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/05/19        youn       최초 생성
 *
 * Hibernate가 엔티티 이름을 실제 DB 테이블·컬럼 이름으로 바꿀 때 쓰는 규칙이다.
 * 예: userEmail → USER_EMAIL. application.yml의 physical-strategy 설정으로 연결된다.
 * @Table·@Column에 적은 이름도 이 규칙을 한 번 더 거친다.
 */
public class PhysicalNamingStrategyImpl extends PhysicalNamingStrategyStandardImpl {

    private static final long serialVersionUID = 1L;
    public static final PhysicalNamingStrategyImpl INSTANCE = new PhysicalNamingStrategyImpl();

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        return new Identifier(addUnderscores(name.getText()), name.isQuoted());
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        return new Identifier(addUnderscores(name.getText()), name.isQuoted());
    }

    /**
     * 소문자-대문자-소문자 경계에만 밑줄을 넣고 전체를 대문자로 바꾼다.
     * 이미 USER_EMAIL처럼 대문자인 이름은 그대로 유지된다.
     */
    protected static String addUnderscores(String name) {
        final StringBuilder buf = new StringBuilder(name);
        for (int i = 1; i < buf.length() - 1; i++) {
            if (Character.isLowerCase(buf.charAt(i - 1)) &&
                    Character.isUpperCase(buf.charAt(i)) &&
                    Character.isLowerCase(buf.charAt(i + 1))) {
                buf.insert(i++, '_');
            }
        }
        return buf.toString().toUpperCase(Locale.ROOT);
    }
}