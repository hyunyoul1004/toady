package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementCommentRequest;
import com.toady.web.app.announcement.dto.AnnouncementCommentResponse;
import com.toady.web.app.announcement.dto.AnnouncementCommentUpdateRequest;
import com.toady.web.app.announcement.entity.AnnouncementCommentEntity;
import com.toady.web.app.announcement.repository.AnnouncementCommentRepository;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.app.user.service.ProfileIcons;
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
    private final ProfileIcons profileIcons;

    public Page<AnnouncementCommentResponse> list(
            AnnouncementType type,
            String houseManageNo,
            String pblancNo,
            int page,
            int size,
            Long beforeCommentSeq
    ) {
        if (beforeCommentSeq != null && (beforeCommentSeq <= 0 || page != 0)) {
            throw new IllegalArgumentException("댓글 커서 조회는 양수 댓글 번호와 page=0을 사용하세요.");
        }
        requireAnnouncement(type, houseManageNo, pblancNo);
        Page<AnnouncementCommentResponse> comments = commentRepository.findVisibleComments(
                type.name(), houseManageNo, pblancNo, beforeCommentSeq, PageRequest.of(page, size)
        );
        // 작성자 프로필 캐릭터를 한 번에 읽어 붙인다(테이블이 없으면 모두 null → 닉네임 첫 글자).
        var icons = profileIcons.of(comments.getContent().stream().map(AnnouncementCommentResponse::createUserSeq).toList());
        return comments.map(comment -> comment.withProfileIcon(icons.get(comment.createUserSeq())));
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
            AnnouncementCommentEntity parent = commentRepository.findForUpdate(request.parentCommentSeq())
                    .orElseThrow(() -> new EntityNotFoundException("부모 댓글을 찾을 수 없습니다."));
            if (!"N".equals(parent.getDeleteYn())
                    || !type.name().equals(parent.getAnnouncementType())
                    || !houseManageNo.equals(parent.getHouseManageNo())
                    || !pblancNo.equals(parent.getPblancNo())) {
                throw new EntityNotFoundException("부모 댓글을 찾을 수 없습니다.");
            }
            // 답글은 한 단계만 둔다(화면이 한 단계 트리만 그린다).
            if (parent.getParentCommentSeq() != null) throw new IllegalArgumentException("답글에는 다시 답글을 달 수 없어요.");
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
        LocalDateTime now = LocalDateTime.now();
        comment.setDeleteYn("Y");
        comment.setUpdateDt(now);
        comment.setUpdateUserSeq(userSeq);
        // 답글이 있는 댓글을 지우면 답글도 함께 지운다(부모 없는 답글이 일반 댓글처럼 보이지 않게).
        if (comment.getParentCommentSeq() == null) commentRepository.deleteReplies(comment.getCommentSeq(), userSeq, now);
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
                comment.getUpdateDt(),
                profileIcons.of(comment.getCreateUserSeq())
        );
    }
}
