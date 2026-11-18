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

/**
 * 공고 댓글 조회와 변경을 처리합니다(화면의 '공고 이야기').
 * 흐름: AnnouncementController → 이 클래스 → AnnouncementCommentRepository → TB_LTTOT_PBLANC_COMMENT.
 * 작성자 닉네임은 UserRepository, 프로필 캐릭터는 ProfileIcons, 운영팀 표시·고정은 OfficialComments,
 * 쓰기 정지·금칙어는 Sanctions·ModerationRules, 숨긴 공고는 AnnouncementOverrides에서 확인한다.
 */
// @Service: 스프링 빈으로 등록한다. @RequiredArgsConstructor로 final 필드를 생성자 주입한다.
@Service
@RequiredArgsConstructor
// @Transactional(readOnly = true): 기본은 조회 전용 트랜잭션. 변경 감지를 하지 않아 가볍고, 실수로 바꾼 값이 저장되지 않는다.
// 쓰기 메서드에는 따로 @Transactional을 붙여 읽기·쓰기 트랜잭션으로 덮어쓴다.
@Transactional(readOnly = true)
public class AnnouncementCommentService {
    /** 숨긴 공고에 쓰기·고치기를 막을 때 보여 주는 안내. */
    static final String HIDDEN_MESSAGE = "운영자가 숨긴 공고라 이야기를 쓸 수 없어요.";
    private final AnnouncementCommentRepository commentRepository;
    private final AnnouncementService announcementService;
    private final UserRepository userRepository;
    private final ProfileIcons profileIcons;
    private final com.toady.web.app.operation.Sanctions sanctions;
    private final com.toady.web.app.operation.ModerationRules moderation;
    private final com.toady.web.app.operation.AnnouncementOverrides overrides;
    private final com.toady.web.app.operation.OfficialComments officials;

    /**
     * 공고의 댓글 목록을 최신순으로 돌려준다.
     * beforeCommentSeq가 있으면 커서 방식(그 번호보다 오래된 것)이라 page는 0이어야 한다.
     * 프로필 캐릭터와 운영팀 여부는 페이지 단위로 한 번에 읽어 붙인다(댓글마다 쿼리하지 않는다).
     */
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
        // 운영팀이 쓴 글에는 표시를 붙인다(화면이 '운영팀' 배지를 단다).
        var official = officials.officialOf(comments.getContent().stream().map(AnnouncementCommentResponse::commentSeq).toList());
        return comments.map(comment -> comment.withProfileIcon(icons.get(comment.createUserSeq()))
                .withOfficial(official.containsKey(comment.commentSeq()), Boolean.TRUE.equals(official.get(comment.commentSeq()))));
    }

    /** 공고 이야기 맨 위에 고정한 운영팀 원글. */
    public java.util.List<AnnouncementCommentResponse> pinned(AnnouncementType type, String houseManageNo, String pblancNo) {
        requireAnnouncement(type, houseManageNo, pblancNo);
        return officials.pinned(type.name(), houseManageNo, pblancNo).stream()
                .map(seq -> commentRepository.findById(seq).filter(row -> "N".equals(row.getDeleteYn())).map(this::toResponse).map(row -> row.withOfficial(true, true)).orElse(null))
                .filter(java.util.Objects::nonNull).toList();
    }

    /** 삭제되지 않은 댓글·답글 수. 공고 상세 응답의 commentCount에 쓴다. */
    public long count(AnnouncementType type, String houseManageNo, String pblancNo) {
        return commentRepository.countByAnnouncementTypeAndHouseManageNoAndPblancNoAndDeleteYn(
                type.name(), houseManageNo, pblancNo, "N"
        );
    }

    /** 댓글·답글을 쓴다. 공고 존재 → 숨김 여부 → 쓰기 정지·금칙어 → (답글이면) 부모 확인 순으로 검사한다. */
    // @Transactional: 읽기·쓰기 트랜잭션. 메서드가 끝나면 커밋되고, 예외(RuntimeException)가 나면 모두 되돌린다.
    @Transactional
    public AnnouncementCommentResponse create(
            AnnouncementType type,
            String houseManageNo,
            String pblancNo,
            AnnouncementCommentRequest request,
            Long userSeq
    ) {
        requireAnnouncement(type, houseManageNo, pblancNo);
        // 운영자가 숨긴 공고에는 새 이야기를 받지 않는다.
        if (overrides.hidden(type.name(), houseManageNo, pblancNo)) throw new AccessDeniedException(HIDDEN_MESSAGE);
        checkWriter(userSeq, request.content());

        if (request.parentCommentSeq() != null) {
            // 부모 댓글을 잠그고 읽는다. 같은 순간 부모가 삭제되면 그 트랜잭션이 끝날 때까지 기다린다.
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

        // save(): 새 엔티티라 INSERT한다. IDENTITY 키라 바로 실행되어 번호가 채워진다.
        AnnouncementCommentEntity saved = commentRepository.save(new AnnouncementCommentEntity(
                type.name(), houseManageNo, pblancNo, request.parentCommentSeq(), request.content(), userSeq
        ));
        return toResponse(saved);
    }

    /**
     * 본인 댓글 내용을 고친다. save()를 부르지 않아도 된다.
     * 트랜잭션 안에서 읽은 엔티티의 값을 바꾸면 커밋할 때 JPA가 UPDATE를 보낸다(변경 감지).
     */
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
        // 숨긴 공고에는 글을 고칠 수도 없게 한다(쓰기와 같은 규칙). 지우기는 본인 글을 정리할 수 있게 그대로 둔다.
        if (overrides.hidden(comment.getAnnouncementType(), comment.getHouseManageNo(), comment.getPblancNo())) throw new AccessDeniedException(HIDDEN_MESSAGE);
        checkWriter(userSeq, request.content());
        comment.setContent(request.content());
        comment.setUpdateDt(LocalDateTime.now());
        comment.setUpdateUserSeq(userSeq);
        return toResponse(comment);
    }

    /** 본인 댓글을 소프트 삭제한다(DELETE_YN = 'Y'). 원글이면 답글도 함께 지운다. */
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

    /** 이야기 쓰기 정지(관리자 제재)와 금칙어. 걸리면 그 이유를 그대로 화면에 보여 준다. */
    private void checkWriter(Long userSeq, String content) {
        String banned = sanctions.storyBanMessage(userSeq);
        if (banned != null) throw new AccessDeniedException(banned);
        if (moderation.check(content, com.toady.web.app.operation.ModerationRules.Scope.STORY) == com.toady.web.app.operation.ModerationRules.Action.BLOCK) {
            throw new IllegalArgumentException("운영 정책상 쓸 수 없는 표현이 들어 있어요. 내용을 고쳐 다시 올려 주세요.");
        }
    }

    /** 삭제되지 않았고, 요청한 공고에 속하고, 본인이 쓴 댓글만 돌려준다. 다른 공고 댓글이면 404, 남의 댓글이면 403. */
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

    /** 없는 공고에 댓글을 달거나 조회하지 못하게 먼저 확인한다. */
    private void requireAnnouncement(AnnouncementType type, String houseManageNo, String pblancNo) {
        if (!announcementService.exists(type, houseManageNo, pblancNo)) {
            throw new EntityNotFoundException("공고를 찾을 수 없습니다.");
        }
    }

    /** 엔티티 하나를 응답으로 바꾼다. 작성자 닉네임·프로필 캐릭터를 따로 읽는다. 작성·수정 응답과 고정 글에 쓴다. */
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
                profileIcons.of(comment.getCreateUserSeq()),
                false,
                false
        );
    }
}
