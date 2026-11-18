package com.toady.web.app.user.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * packageName    : com.toady.web.app.user.entity
 * fileName       : UserEntity
 * author         : youn
 * date           : 2022/06/06
 * description    : 회원 정보
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/06        youn       최초 생성
 *
 * TB_USER 테이블과 연결된 JPA 엔티티. 회원 계정 한 명이 한 행이다.
 * 상태 값은 Y/N 문자(emailCertifyYn, lockYn, withdrawalYn)로 둔다. 로그인·토큰 검증이 이 값들을 본다.
 * UserService·AccountService·CertifyService가 UserRepository로 읽고 바꾼다.
 */
// @DynamicUpdate: 바뀐 컬럼만 UPDATE한다. 관리자 사이트가 같은 행의 다른 컬럼(숨김·잠금)을 따로 바꾸므로, 읽어 둔 값으로 전체를 덮어쓰지 않게 한다.
@DynamicUpdate
// @Entity: JPA가 관리하는 엔티티. 이 클래스의 필드가 테이블 컬럼과 연결된다
@Entity
// @Builder/@AllArgsConstructor: 가입 때 UserEntity.builder()...build()로 만든다. @NoArgsConstructor: JPA가 조회 결과로 객체를 만들 때 필요
@Builder
@AllArgsConstructor
@NoArgsConstructor
// @Getter/@Setter: 필드 접근자. 트랜잭션 안에서 setter로 값을 바꾸면 커밋 때 UPDATE된다(변경 감지)
@Getter
@Setter
// @Table: 실제 테이블 이름과 유니크 제약(이메일·닉네임 중복 금지)
@Table(
        name = "TB_USER",
        uniqueConstraints = {
                @UniqueConstraint(
                        name="uk_subscript_email",
                        columnNames={"userEmail"}
                ),
                @UniqueConstraint(
                        name="uk_subscript_userNicName",
                        columnNames={"userNicName"}
                )
        }
)
public class UserEntity {

    // @Id: 기본 키. @GeneratedValue(IDENTITY): DB의 AUTO_INCREMENT로 번호를 받는다(INSERT 후 값이 채워진다)
    // @Column: 컬럼 제약(nullable=false → NOT NULL, length → 길이). 이름은 PhysicalNamingStrategyImpl이 USER_SEQ처럼 바꾼다
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, length = 20)
    private Long userSeq;

    // @Comment: 테이블을 자동 생성할 때 붙는 컬럼 설명(Hibernate)
    @Column(nullable = false, length = 100)
    @Comment("사용자 emailId")
    private String userEmail;

    // 평문이 아니라 BCrypt 해시가 들어간다
    @Column(nullable = false, length = 150)
    @Comment("사용자 비밀번호")
    private String userPassword;

    @Column(nullable = false, length = 50)
    @Comment("사용자 닉네임.")
    private String userNicName;

    // @ColumnDefault: 테이블 생성 시 DEFAULT 'N'을 붙인다
    @Column(nullable = false, length = 1)
    @ColumnDefault("'N'")
    @Comment("이메일 인증 여부")
    private String emailCertifyYn;

    @Column(nullable = false, length = 1)
    @ColumnDefault("'N'")
    @Comment("락 유무")
    private String lockYn;

    @Comment("락걸린 일자.")
    private LocalDateTime lockDt;

    @Column(nullable = false, length = 1)
    @ColumnDefault("'N'")
    @Comment("탈퇴유무")
    private String withdrawalYn;

    @Comment("탈퇴일자")
    private LocalDateTime withdrawalDt;

    // @CreationTimestamp: 처음 INSERT할 때 Hibernate가 현재 시각을 넣는다
    @Comment("등록일시")
    @CreationTimestamp
    private LocalDateTime createDt;

    @Comment("수정일시")
    private LocalDateTime updateDt;

}
