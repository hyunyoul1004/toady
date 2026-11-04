package com.toady.web.app.board.repository;

import com.toady.web.app.board.entity.BoardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * packageName    : com.toady.web.app.board.repository
 * fileName       : BoardRepository
 * author         : youn
 * date           : 2023/08/15
 * description    : 게시판 repo
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/08/15        youn       최초 생성
 */
public interface BoardRepository extends JpaRepository<BoardEntity, Long>, JpaSpecificationExecutor<BoardEntity> {

}
