package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.RemainingCompetitionEntity;
import com.toady.web.app.announcement.entity.id.RemainingCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 잔여세대 공고의 주택형·사전/사후 구분별 경쟁률을 조회합니다. */
public interface RemainingCompetitionRepository
        extends JpaRepository<RemainingCompetitionEntity, RemainingCompetitionId> {
    List<RemainingCompetitionEntity>
    findByHouseManageNoAndPblancNoOrderByHouseTyAscRemndrHshldPblancTycdAsc(
            String houseManageNo, String pblancNo
    );
}
