package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.UrbanCompetitionEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 오피스텔·도시형 등 공고의 경쟁률을 주택형 순으로 조회합니다. */
public interface UrbanCompetitionRepository extends JpaRepository<UrbanCompetitionEntity, AnnouncementModelId> {
    List<UrbanCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(
            String houseManageNo, String pblancNo
    );
}
