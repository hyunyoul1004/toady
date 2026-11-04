package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AptCompetitionEntity;
import com.toady.web.app.announcement.entity.id.AptCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** APT 공고의 경쟁률을 주택형·순위·거주지역 순으로 조회합니다. */
public interface AptCompetitionRepository extends JpaRepository<AptCompetitionEntity, AptCompetitionId> {
    List<AptCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscSubscrptRankCodeAscResideSecdAsc(
            String houseManageNo, String pblancNo
    );
}
