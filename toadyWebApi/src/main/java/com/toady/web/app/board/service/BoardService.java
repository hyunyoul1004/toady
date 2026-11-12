package com.toady.web.app.board.service;

import com.toady.web.app.board.dto.BoardRequestDto;
import com.toady.web.app.board.entity.BoardEntity;
import com.toady.web.app.board.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시판 검색을 처리합니다. */
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    /** 노출 중인 게시글을 검색 조건과 페이지 정보에 따라 조회합니다. */
    @Transactional(readOnly = true)
    public Page<BoardEntity>  getBoardList(BoardRequestDto.Search search, Pageable pageable){

        Specification<BoardEntity> specification = (root, query, builder) -> {
            var predicates = builder.and(
                    builder.equal(root.get("useYn"), "Y"),
                    builder.equal(root.get("deleteYn"), "N")
            );
            // 예약 게시글은 시작 전·종료 후에 공개 목록에 나타나지 않는다. null 경계는 제한 없음이다.
            var now = java.time.LocalDateTime.now();
            predicates = builder.and(predicates, builder.or(
                    builder.equal(root.get("boardTargetDateYn"), "N"),
                    builder.and(builder.equal(root.get("boardTargetDateYn"), "Y"),
                            builder.or(builder.isNull(root.get("boardStartDate")), builder.lessThanOrEqualTo(root.get("boardStartDate"), now)),
                            builder.or(builder.isNull(root.get("boardEndDate")), builder.greaterThanOrEqualTo(root.get("boardEndDate"), now)))));
            if (search.getBoardType() != null && !search.getBoardType().isBlank()) {
                predicates = builder.and(predicates, builder.equal(root.get("boardType"), search.getBoardType()));
            }
            if (search.getBoardLocation() != null
                    && !search.getBoardLocation().isBlank()
                    && !"ALL".equals(search.getBoardLocation())) {
                predicates = builder.and(predicates, builder.equal(root.get("boardLocation"), search.getBoardLocation()));
            }
            if (search.getBoardTitle() != null && !search.getBoardTitle().isBlank()) {
                predicates = builder.and(
                        predicates,
                        builder.like(builder.lower(root.get("boardTitle")),
                                "%" + search.getBoardTitle().trim().toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", '\\')
                );
            }
            return predicates;
        };
        return boardRepository.findAll(specification, pageable);
    }

}
