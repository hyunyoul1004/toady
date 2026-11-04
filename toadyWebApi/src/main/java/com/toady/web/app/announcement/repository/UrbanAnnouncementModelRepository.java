package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.UrbanAnnouncementModelEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementModelId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** 도시형·오피스텔 공고의 주택형 상세 정보를 저장하고 조회합니다. */
public interface UrbanAnnouncementModelRepository extends JpaRepository<UrbanAnnouncementModelEntity, AnnouncementModelId> {
    List<UrbanAnnouncementModelEntity> findByHouseManageNoAndPblancNoOrderByModelNoAsc(String houseManageNo, String pblancNo);
}
