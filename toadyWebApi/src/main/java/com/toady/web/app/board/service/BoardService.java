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
                                "%" + search.getBoardTitle().trim().toLowerCase() + "%")
                );
            }
            return predicates;
        };
        return boardRepository.findAll(specification, pageable);
    }

}
