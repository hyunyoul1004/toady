package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.RemainingAnnouncementEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 잔여세대 공고를 기본 키와 동적 검색 조건으로 조회합니다. */
public interface RemainingAnnouncementRepository extends JpaRepository<RemainingAnnouncementEntity, AnnouncementId>, JpaSpecificationExecutor<RemainingAnnouncementEntity> {
}
