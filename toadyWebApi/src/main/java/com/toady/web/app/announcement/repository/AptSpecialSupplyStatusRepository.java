package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AptSpecialSupplyStatusEntity;
import com.toady.web.app.announcement.entity.id.HouseTypeAnnouncementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * APT 공고의 주택형별 특별공급 신청현황을 조회합니다.
 * 테이블: TB_APT_SPSPLY_REQST_STUS, 키: HouseTypeAnnouncementId. 스케줄러가 청약홈 결과를 모아 넣고 API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. 호출: AnnouncementCompetitionService.
 */
public interface AptSpecialSupplyStatusRepository
        extends JpaRepository<AptSpecialSupplyStatusEntity, HouseTypeAnnouncementId> {
    // 메서드 이름으로 쿼리를 만드는 파생 쿼리. 한 공고의 행을 주택형 순으로 정렬해 돌려준다.
    List<AptSpecialSupplyStatusEntity> findByHouseManageNoAndPblancNoOrderByHouseTyAsc(
            String houseManageNo, String pblancNo
    );
}
