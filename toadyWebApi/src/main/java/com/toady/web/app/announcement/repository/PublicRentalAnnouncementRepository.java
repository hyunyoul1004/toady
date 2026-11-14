package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.PublicRentalAnnouncementEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 공공지원 민간임대 공고를 기본 키와 동적 검색 조건으로 조회합니다. */
public interface PublicRentalAnnouncementRepository extends JpaRepository<PublicRentalAnnouncementEntity, AnnouncementId>, JpaSpecificationExecutor<PublicRentalAnnouncementEntity> {
}
