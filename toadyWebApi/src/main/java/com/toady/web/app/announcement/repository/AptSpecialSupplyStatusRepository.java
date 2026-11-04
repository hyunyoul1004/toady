package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AptSpecialSupplyStatusEntity;
import com.toady.web.app.announcement.entity.id.HouseTypeAnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** APT 공고의 주택형별 특별공급 신청현황을 조회합니다. */
public interface AptSpecialSupplyStatusRepository
        extends JpaRepository<AptSpecialSupplyStatusEntity, HouseTypeAnnouncementId> {
    List<AptSpecialSupplyStatusEntity> findByHouseManageNoAndPblancNoOrderByHouseTyAsc(
            String houseManageNo, String pblancNo
    );
}
