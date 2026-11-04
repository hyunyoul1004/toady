package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.OptionalAnnouncementModelEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 임의공급 공고의 주택형 상세 정보를 저장하고 조회합니다. */
public interface OptionalAnnouncementModelRepository extends JpaRepository<OptionalAnnouncementModelEntity, AnnouncementModelId> {
    List<OptionalAnnouncementModelEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(String houseManageNo, String pblancNo);
}
