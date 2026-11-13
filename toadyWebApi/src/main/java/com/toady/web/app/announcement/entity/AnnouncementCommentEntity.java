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

/** 공고 댓글과 답글, 작성·수정·삭제 상태를 저장합니다. */
// 바뀐 컬럼만 UPDATE한다. 관리자 사이트가 같은 행의 다른 컬럼(숨김·잠금)을 따로 바꾸므로, 읽어 둔 값으로 전체를 덮어쓰지 않게 한다.
@DynamicUpdate
@Entity
@Table(name = "TB_LTTOT_PBLANC_COMMENT")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnnouncementCommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMMENT_SEQ")
    private Long commentSeq;

    @Column(name = "ANNOUNCEMENT_TYPE", nullable = false, length = 20)
    private String announcementType;

    @Column(name = "HOUSE_MANAGE_NO", nullable = false, length = 40)
    private String houseManageNo;

    @Column(name = "PBLANC_NO", nullable = false, length = 40)
    private String pblancNo;

    @Column(name = "PARENT_COMMENT_SEQ")
    private Long parentCommentSeq;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "RECOMMEND", nullable = false)
    private Integer recommend = 0;

    @Column(name = "OPPOSE", nullable = false)
    private Integer oppose = 0;

    @Column(name = "DELETE_YN", nullable = false, length = 1)
    private String deleteYn = "N";

    @Column(name = "CREATE_USER_SEQ", nullable = false)
    private Long createUserSeq;

    @CreationTimestamp
    @Column(name = "REG_DT", nullable = false, updatable = false)
    private LocalDateTime regDt;

    @Column(name = "UPDATE_USER_SEQ")
    private Long updateUserSeq;

    @Column(name = "UPDATE_DT")
    private LocalDateTime updateDt;

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
