package com.toady.web.app.certify.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * packageName    : com.toady.web.app.certify.entity
 * fileName       : CertifyEntity
 * author         : youn
 * date           : 2022/06/11
 * description    : 인증 정보
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 *
 * TB_CERTIFY 테이블 엔티티. 인증 코드를 발급할 때마다 한 행이 생긴다.
 * certifyYn이 N이면 아직 쓰지 않은 코드, Y면 사용했거나 폐기한 코드다. 만료는 createDt 기준으로 서비스가 계산한다.
 * CertifyService가 CertifyRepository로 만들고 읽는다.
 */
// @Entity: JPA 엔티티. @Builder 등 Lombok 애너테이션은 UserEntity와 같은 용도다
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(
        name = "TB_CERTIFY"
)
public class CertifyEntity {

    // @Id + @GeneratedValue(IDENTITY): AUTO_INCREMENT 기본 키
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, length = 20)
    private Long certifySeq;

    @Column(nullable = false, length = 10)
    // 실제 값은 ACCOUNT(가입 인증)와 FIND_PW(비밀번호 찾기) 두 가지다
    @Comment("인증 타입 (ACCOUNT)")
    private String certifyType;

    @Column(nullable = false, length = 10)
    @Comment("전송 타입 (EMAIL, KAKAO, SMS ...)")
    private String certifySendType;

    @Column(nullable = false, length = 50)
    @Comment("인증 값")
    private String certifyCode;

    // columnDefinition: 컬럼 타입을 직접 지정한다(긴 본문이라 TEXT)
    @Column(nullable = false, columnDefinition = "TEXT")
    @Comment("인증 이메일 본문")
    private String certifyMessage;

    @Column(nullable = false, length = 1)
    @ColumnDefault("'N'")
    @Comment("인증 유무")
    private String certifyYn;

    @Comment("등록자")
    @Column(nullable = false, length = 20)
    private Long createUserSeq;

    // @CreationTimestamp: INSERT 때 현재 시각 자동 입력
    @Comment("등록일시")
    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createDt;

    @Comment("수정자")
    @Column(length = 20)
    private Long updateUserSeq;

    @Comment("수정일시")
    private LocalDateTime updateDt;

}
