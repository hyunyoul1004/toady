package com.toady.web.app.user.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

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
 */
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, length = 20)
    private Long userSeq;

    @Column(nullable = false, length = 100)
    @Comment("사용자 emailId")
    private String userEmail;

    @Column(nullable = false, length = 150)
    @Comment("사용자 비밀번호")
    private String userPassword;

    @Column(nullable = false, length = 50)
    @Comment("사용자 닉네임.")
    private String userNicName;

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

    @Comment("등록일시")
    @CreationTimestamp
    private LocalDateTime createDt;

    @Comment("수정일시")
    private LocalDateTime updateDt;

}
