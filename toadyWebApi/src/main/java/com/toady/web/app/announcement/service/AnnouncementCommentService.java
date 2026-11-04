package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementCommentRequest;
import com.toady.web.app.announcement.dto.AnnouncementCommentResponse;
import com.toady.web.app.announcement.dto.AnnouncementCommentUpdateRequest;
import com.toady.web.app.announcement.entity.AnnouncementCommentEntity;
import com.toady.web.app.announcement.repository.AnnouncementCommentRepository;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 공고 댓글 조회와 변경을 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementCommentService {
    private final AnnouncementCommentRepository commentRepository;
    private final AnnouncementService announcementService;
    private final UserRepository userRepository;

    public Page<AnnouncementCommentResponse> list(
            AnnouncementType type,
            String houseManageNo,
            String pblancNo,
            int page,
            int size
    ) {
        requireAnnouncement(type, houseManageNo, pblancNo);
        return commentRepository.findVisibleComments(
                type.name(), houseManageNo, pblancNo, PageRequest.of(page, size)
        );
    }

    public long count(AnnouncementType type, String houseManageNo, String pblancNo) {
        return commentRepository.countByAnnouncementTypeAndHouseManageNoAndPblancNoAndDeleteYn(
                type.name(), houseManageNo, pblancNo, "N"
        );
    }

    @Transactional
    public AnnouncementCommentResponse create(
            AnnouncementType type,
            String houseManageNo,
            String pblancNo,
            AnnouncementCommentRequest request,
            Long userSeq
    ) {
        requireAnnouncement(type, houseManageNo, pblancNo);

        if (request.parentCommentSeq() != null) {
            AnnouncementCommentEntity parent = commentRepository.findById(request.parentCommentSeq())
                    .orElseThrow(() -> new EntityNotFoundException("부모 댓글을 찾을 수 없습니다."));
            if (!"N".equals(parent.getDeleteYn())
                    || !type.name().equals(parent.getAnnouncementType())
                    || !houseManageNo.equals(parent.getHouseManageNo())
                    || !pblancNo.equals(parent.getPblancNo())) {
                throw new EntityNotFoundException("부모 댓글을 찾을 수 없습니다.");
            }
        }

        AnnouncementCommentEntity saved = commentRepository.save(new AnnouncementCommentEntity(
                type.name(), houseManageNo, pblancNo, request.parentCommentSeq(), request.content(), userSeq
        ));
        return toResponse(saved);
    }

    @Transactional
    public AnnouncementCommentResponse update(
            AnnouncementType type,
            String houseManageNo,
            String pblancNo,
            Long commentSeq,
            AnnouncementCommentUpdateRequest request,
            Long userSeq
    ) {
        AnnouncementCommentEntity comment = ownedComment(
                commentSeq, userSeq, type, houseManageNo, pblancNo
        );
        comment.setContent(request.content());
        comment.setUpdateDt(LocalDateTime.now());
        comment.setUpdateUserSeq(userSeq);
        return toResponse(comment);
    }

    @Transactional
    public void delete(
            AnnouncementType type,
            String houseManageNo,
            String pblancNo,
            Long commentSeq,
            Long userSeq
    ) {
        AnnouncementCommentEntity comment = ownedComment(
                commentSeq, userSeq, type, houseManageNo, pblancNo
        );
        comment.setDeleteYn("Y");
        comment.setUpdateDt(LocalDateTime.now());
        comment.setUpdateUserSeq(userSeq);
    }

    private AnnouncementCommentEntity ownedComment(
            Long commentSeq,
            Long userSeq,
            AnnouncementType type,
            String houseManageNo,
            String pblancNo
    ) {
        AnnouncementCommentEntity comment = commentRepository.findById(commentSeq)
                .filter(row -> "N".equals(row.getDeleteYn()))
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));
        if (type != null && (!type.name().equals(comment.getAnnouncementType())
                || !houseManageNo.equals(comment.getHouseManageNo())
                || !pblancNo.equals(comment.getPblancNo()))) {
            throw new EntityNotFoundException("댓글을 찾을 수 없습니다.");
        }
        if (!comment.getCreateUserSeq().equals(userSeq)) {
            throw new AccessDeniedException("댓글 작성자만 수정 또는 삭제할 수 있습니다.");
        }
        return comment;
    }

    private void requireAnnouncement(AnnouncementType type, String houseManageNo, String pblancNo) {
        if (!announcementService.exists(type, houseManageNo, pblancNo)) {
            throw new EntityNotFoundException("공고를 찾을 수 없습니다.");
        }
    }

    private AnnouncementCommentResponse toResponse(AnnouncementCommentEntity comment) {
        UserEntity user = userRepository.findById(comment.getCreateUserSeq())
                .orElseThrow(() -> new EntityNotFoundException("댓글 작성자를 찾을 수 없습니다."));
        return new AnnouncementCommentResponse(
                comment.getCommentSeq(),
                comment.getAnnouncementType(),
                comment.getHouseManageNo(),
                comment.getPblancNo(),
                comment.getParentCommentSeq(),
                comment.getContent(),
                comment.getRecommend(),
                comment.getOppose(),
                comment.getDeleteYn(),
                comment.getCreateUserSeq(),
                user.getUserNicName(),
                comment.getRegDt(),
                comment.getUpdateUserSeq(),
                comment.getUpdateDt()
        );
    }
}
