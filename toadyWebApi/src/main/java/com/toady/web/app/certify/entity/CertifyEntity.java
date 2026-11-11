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
 */
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, length = 20)
    private Long certifySeq;

    @Column(nullable = false, length = 10)
    @Comment("인증 타입 (ACCOUNT)")
    private String certifyType;

    @Column(nullable = false, length = 10)
    @Comment("전송 타입 (EMAIL, KAKAO, SMS ...)")
    private String certifySendType;

    @Column(nullable = false, length = 50)
    @Comment("인증 값")
    private String certifyCode;

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
