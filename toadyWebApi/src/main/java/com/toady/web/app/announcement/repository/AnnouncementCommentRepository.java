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

/** 공고 댓글을 저장하고 공개 대상 댓글을 페이지 단위로 조회합니다. */
public interface AnnouncementCommentRepository extends JpaRepository<AnnouncementCommentEntity, Long> {
    /** 삭제되지 않은 공고 댓글을 등록 번호 내림차순으로 조회하며 작성자 닉네임을 함께 반환합니다. */
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AnnouncementCommentEntity c where c.commentSeq = :seq")
    Optional<AnnouncementCommentEntity> findForUpdate(@Param("seq") Long seq);

    /** 부모 댓글을 지울 때 그 답글도 함께 삭제 처리한다(맥락 없는 답글이 일반 댓글처럼 남지 않게). */
    @Modifying(flushAutomatically = true)
    @Query("update AnnouncementCommentEntity c set c.deleteYn = 'Y', c.updateDt = :now, c.updateUserSeq = :userSeq where c.parentCommentSeq = :parentSeq and c.deleteYn = 'N'")
    int deleteReplies(@Param("parentSeq") Long parentSeq, @Param("userSeq") Long userSeq, @Param("now") LocalDateTime now);

    /** 지정한 공고와 삭제 상태 코드에 해당하는 댓글 수를 집계합니다. */
    long countByAnnouncementTypeAndHouseManageNoAndPblancNoAndDeleteYn(
            String announcementType, String houseManageNo, String pblancNo, String deleteYn
    );
}
