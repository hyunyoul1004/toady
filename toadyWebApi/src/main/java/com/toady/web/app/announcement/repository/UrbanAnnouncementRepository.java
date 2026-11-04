package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.UrbanAnnouncementEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

/** 도시형·오피스텔 공고를 기본 키와 동적 검색 조건으로 조회합니다. */
public interface UrbanAnnouncementRepository extends JpaRepository<UrbanAnnouncementEntity, AnnouncementId>, JpaSpecificationExecutor<UrbanAnnouncementEntity> {
}
