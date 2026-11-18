package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AnnouncementCommentEntity;
import com.toady.web.app.announcement.dto.AnnouncementCommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 공고 댓글(공고 이야기, TB_LTTOT_PBLANC_COMMENT)을 저장하고 공개 대상 댓글을 페이지 단위로 조회합니다.
 * JpaRepository<엔티티, 키 타입>을 상속하면 save·findById 같은 기본 메서드가 생긴다.
 * 호출: AnnouncementCommentService(목록·작성·수정·삭제), 댓글 수 집계는 AnnouncementCommentCountService가 직접 JPQL로 한다.
 */
public interface AnnouncementCommentRepository extends JpaRepository<AnnouncementCommentEntity, Long> {
    /**
     * 삭제되지 않은 공고 댓글을 등록 번호 내림차순으로 조회하며 작성자 닉네임을 함께 반환합니다.
     * - select new …(…): 엔티티 대신 DTO 생성자로 바로 담는 DTO 프로젝션. 필요한 값만 읽는다.
     * - left join UserEntity: 연관관계 없이 번호로 조인한다. 회원 행이 없어도 댓글은 나오고 닉네임만 null이 된다.
     * - beforeCommentSeq: 커서 방식 더보기. null이면 조건이 빠지고, 값이 있으면 그 번호보다 오래된 댓글만 본다.
     * - Page 반환: Spring Data가 같은 조건의 count 쿼리를 따로 만들어 전체 개수도 채운다.
     */
    @Query("""
            select new com.toady.web.app.announcement.dto.AnnouncementCommentResponse(
                c.commentSeq, c.announcementType, c.houseManageNo, c.pblancNo,
                c.parentCommentSeq, c.content, c.recommend, c.oppose, c.deleteYn,
                c.createUserSeq, u.userNicName, c.regDt, c.updateUserSeq, c.updateDt
            )
            from AnnouncementCommentEntity c
            left join UserEntity u on u.userSeq = c.createUserSeq
            where c.announcementType = :announcementType
              and c.houseManageNo = :houseManageNo
              and c.pblancNo = :pblancNo
              and c.deleteYn = 'N'
              and (:beforeCommentSeq is null or c.commentSeq < :beforeCommentSeq)
            order by c.commentSeq desc
            """)
    Page<AnnouncementCommentResponse> findVisibleComments(
            @Param("announcementType") String announcementType,
            @Param("houseManageNo") String houseManageNo,
            @Param("pblancNo") String pblancNo,
            @Param("beforeCommentSeq") Long beforeCommentSeq,
            Pageable pageable
    );

    /** 답글을 달 때 부모 댓글을 잠근다(같은 순간의 부모 삭제와 겹쳐 삭제된 부모 아래 답글이 남지 않게). */
    // @Lock(PESSIMISTIC_WRITE): select … for update로 읽어, 트랜잭션이 끝날 때까지 다른 트랜잭션이 이 행을 못 바꾸게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AnnouncementCommentEntity c where c.commentSeq = :seq")
    Optional<AnnouncementCommentEntity> findForUpdate(@Param("seq") Long seq);

    /** 부모 댓글을 지울 때 그 답글도 함께 삭제 처리한다(맥락 없는 답글이 일반 댓글처럼 남지 않게). */
    // @Modifying: update/delete JPQL에는 꼭 붙인다. 엔티티를 하나씩 읽지 않고 DB에 한 번에 반영한다(벌크 연산).
    // flushAutomatically = true: 실행 전에 아직 DB에 안 간 변경(부모 댓글 삭제 표시)을 먼저 flush한다.
    // 반환값 int는 바뀐 행 수다.
    @Modifying(flushAutomatically = true)
    @Query("update AnnouncementCommentEntity c set c.deleteYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.parentCommentSeq = :parentSeq and c.deleteYn = 'N'")
    int deleteReplies(@Param("parentSeq") Long parentSeq, @Param("userSeq") Long userSeq, @Param("now") LocalDateTime now);

    /** 지정한 공고와 삭제 상태 코드에 해당하는 댓글 수를 집계합니다. countBy… 이름만으로 select count(*) 쿼리가 만들어진다. */
    long countByAnnouncementTypeAndHouseManageNoAndPblancNoAndDeleteYn(
            String announcementType, String houseManageNo, String pblancNo, String deleteYn
    );
}
