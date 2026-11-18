package com.toady.web.app.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 공고 댓글과 답글, 작성·수정·삭제 상태를 저장합니다(화면에서는 '공고 이야기').
 * 테이블: TB_LTTOT_PBLANC_COMMENT. 이 패키지에서 유일하게 API가 직접 쓰는 엔티티다.
 * AnnouncementCommentService가 저장·수정하고, 목록은 저장소의 JPQL이 DTO로 바로 읽는다.
 * 삭제는 행을 지우지 않고 DELETE_YN을 'Y'로 바꾼다(소프트 삭제).
 */
// 바뀐 컬럼만 UPDATE한다. 관리자 사이트가 같은 행의 다른 컬럼(숨김·잠금)을 따로 바꾸므로, 읽어 둔 값으로 전체를 덮어쓰지 않게 한다.
@DynamicUpdate
// @Entity: JPA가 관리하는 클래스. 객체 하나가 테이블 한 행이다. @Table로 테이블 이름을 정한다.
@Entity
@Table(name = "TB_LTTOT_PBLANC_COMMENT")
// @Getter/@Setter(Lombok): getter·setter를 만든다. 서비스가 setter로 값을 바꾸면
// 트랜잭션이 끝날 때 JPA가 바뀐 것을 알아채 UPDATE를 보낸다(변경 감지).
@Getter
@Setter
// @NoArgsConstructor(PROTECTED): JPA가 쓰는 빈 생성자. 새 댓글은 아래 생성자로 만든다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementCommentEntity {
    // @Id: 키 컬럼. @GeneratedValue(IDENTITY): 번호는 DB의 AUTO_INCREMENT가 정한다.
    // 그래서 save() 때 INSERT가 바로 실행되고, 만들어진 번호가 commentSeq에 채워진다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_SEQ")
    private Long commentSeq;

    // @Column: 필드와 컬럼을 잇는다. nullable·length는 스키마 정보다(ddl-auto: none이라 실제 검사는 DB 제약이 한다).
    // 공고 유형은 AnnouncementType.name()(APT, URBAN …)으로 저장한다.
    @Column(name = "ANNOUNCEMENT_TYPE", nullable = false, length = 20)
    private String announcementType;

    @Column(name = "HOUSE_MANAGE_NO", nullable = false, length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", nullable = false, length = 40)
    private String pblancNo;

    // 답글이면 부모 댓글 번호, 일반 댓글이면 null. 답글은 한 단계까지만 허용한다.
    @Column(name = "PARENT_COMMENT_SEQ")
    private Long parentCommentSeq;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 필드 초기값(0, "N")은 new로 만든 새 댓글의 기본값이다.
    @Column(name = "RECOMMEND", nullable = false)
    private Integer recommend = 0;

    @Column(name = "OPPOSE", nullable = false)
    private Integer oppose = 0;

    // N: 보임, Y: 삭제됨. 목록·개수는 N만 센다.
    @Column(name = "DELETE_YN", nullable = false, length = 1)
    private String deleteYn = "N";

    @Column(name = "CREATE_USER_SEQ", nullable = false)
    private Long createUserSeq;

    // @CreationTimestamp(Hibernate): INSERT할 때 현재 시각을 넣는다. updatable = false라 이후에는 바뀌지 않는다.
    @CreationTimestamp
    @Column(name = "REG_DT", nullable = false, updatable = false)
    private LocalDateTime regDt;

    @Column(name = "UPDATE_USER_SEQ")
    private Long updateUserSeq;

    @Column(name = "UPDATE_DT")
    private LocalDateTime updateDt;

    /** 새 댓글을 만든다. 번호·등록일시·추천 수 같은 값은 DB와 Hibernate가 채운다. */
    public AnnouncementCommentEntity(
            String announcementType,
            String houseManageNo,
            String pblancNo,
            Long parentCommentSeq,
            String content,
            Long createUserSeq
    ) {
        this.announcementType = announcementType;
        this.houseManageNo = houseManageNo;
        this.pblancNo = pblancNo;
        this.parentCommentSeq = parentCommentSeq;
        this.content = content;
        this.createUserSeq = createUserSeq;
    }
}
