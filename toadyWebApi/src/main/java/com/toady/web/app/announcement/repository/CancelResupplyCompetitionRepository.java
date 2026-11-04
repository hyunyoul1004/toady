package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.CancelResupplyCompetitionEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 취소 후 재공급 공고의 주택형별 경쟁률을 조회합니다. */
public interface CancelResupplyCompetitionRepository
        extends JpaRepository<CancelResupplyCompetitionEntity, AnnouncementModelId> {
    List<CancelResupplyCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(
            String houseManageNo, String pblancNo
    );
}
