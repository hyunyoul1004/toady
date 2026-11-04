package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AptScoreEntity;
import com.toady.web.app.announcement.entity.id.AptScoreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** APT 공고의 주택형·거주지역별 당첨가점을 조회합니다. */
public interface AptScoreRepository extends JpaRepository<AptScoreEntity, AptScoreId> {
    List<AptScoreEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscResideSecdAsc(
            String houseManageNo, String pblancNo
    );
}
