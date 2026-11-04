package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.PublicRentalCompetitionEntity;
import com.toady.web.app.announcement.entity.id.PublicRentalCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 공공지원 민간임대 공고의 공급유형별 경쟁률을 조회합니다. */
public interface PublicRentalCompetitionRepository
        extends JpaRepository<PublicRentalCompetitionEntity, PublicRentalCompetitionId> {
    List<PublicRentalCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscSpsplyKndCodeAsc(
            String houseManageNo, String pblancNo
    );
}
