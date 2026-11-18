package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.OptionalAnnouncementEntity;
import com.toady.web.app.announcement.entity.id.AnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 임의공급 공고(TB_OPT_LTTOT_PBLANC_DETAIL) 저장소. 데이터는 스케줄러가 청약홈에서 모아 넣고, API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 실행할 때 구현체를 만들어 빈으로 등록한다(@Repository를 붙이지 않아도 된다).
 * - JpaRepository: findById·existsById·findAll 같은 기본 메서드를 그대로 쓴다. 키 타입은 복합키 AnnouncementId.
 * - JpaSpecificationExecutor: 검색 조건을 Specification 객체로 조립해 findAll(spec, pageable)·findBy(spec, …)로 조회한다.
 * 호출: AnnouncementSearchService(목록·자동완성), AnnouncementDetailService(상세·존재 확인).
 */
public interface OptionalAnnouncementRepository extends JpaRepository<OptionalAnnouncementEntity, AnnouncementId>, JpaSpecificationExecutor<OptionalAnnouncementEntity> {
}
