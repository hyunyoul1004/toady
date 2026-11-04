package com.toady.web.app.board.entity;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * packageName    : com.toady.web.app.board.entity
 * fileName       : BoardEntity
 * author         : youn
 * date           : 2023/08/02
 * description    : 게시판 Entity
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/08/02        youn       최초 생성
 */
@Entity //JPA가 관리하는 클래스로 JPA를 사용해서 테이블과 매핑할 클래스는 반드시 @Entity 를 붙여야 한다.
@Builder //객체를 생성할 때 Builder를 자동으로 추가해준다.
@AllArgsConstructor //클래스에 존재하는 모든 필드에 대한 생성자를 자동으로 생성해줍니다.
@NoArgsConstructor // 파라미터가 없는 생성자를 생성합니다.
@Getter
@Setter
@Table(name = "TB_BOARD")
public class BoardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, length = 20)
    private Long boardSeq;

    @Comment("게시판 타입")
    @Column(nullable = false, length = 10)
    private String boardType;

    @Comment("게시판 지역")
    @Column(length = 20)
    private String boardLocation;

    @Comment("게시판 제목")
    @Column(nullable = false, length = 100)
    private String boardTitle;

    @Comment("게시판 내용")
    private String boardContents;

    @Comment("사용 유무")
    @Column(nullable = false, length = 1)
    @ColumnDefault("'Y'")
    private String useYn;

    @Comment("삭제 유무")
    @Column(nullable = false, length = 1)
    @ColumnDefault("'N'")
    private String deleteYn;

    @Comment("삭제 일자")
    private LocalDateTime deleteDt;

    @Comment("파일 Seq")
    @Column(length = 20)
    private Long fileSeq;

    @Comment("게시 시작/종료 사용 유무")
    @Column(nullable = false, length = 1)
    @ColumnDefault("'N'")
    private String boardTargetDateYn;

    @Comment("게시 시작일")
    private LocalDateTime boardStartDate;

    @Comment("게시 종료일")
    private LocalDateTime boardEndDate;

    @Comment("생성일")
    @Column(nullable = false)
    @CreationTimestamp
    private LocalDateTime createDt;

    @Comment("생성자 seq")
    @Column(nullable = false, length = 20)
    private Long createUserSeq;

    @Comment("수정일")
    private LocalDateTime updateDt;

    @Comment("수정자 Seq")
    @Column(length = 20)
    private Long updateUserSeq;

}

