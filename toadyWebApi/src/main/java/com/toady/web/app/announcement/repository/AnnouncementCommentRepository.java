package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AnnouncementCommentEntity;
import com.toady.web.app.announcement.dto.AnnouncementCommentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 지정한 공고와 삭제 상태 코드에 해당하는 댓글 수를 집계합니다. */
    long countByAnnouncementTypeAndHouseManageNoAndPblancNoAndDeleteYn(
            String announcementType, String houseManageNo, String pblancNo, String deleteYn
    );
}
