package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.OptionalCompetitionEntity;
import com.toady.web.app.announcement.entity.id.HouseTypeAnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 임의공급 공고의 주택형별 경쟁률을 조회합니다. */
public interface OptionalCompetitionRepository
        extends JpaRepository<OptionalCompetitionEntity, HouseTypeAnnouncementId> {
    List<OptionalCompetitionEntity> findByHouseManageNoAndPblancNoOrderByHouseTyAsc(
            String houseManageNo, String pblancNo
    );
}
