package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.UrbanCompetitionEntity;
import com.toady.web.app.announcement.entity.id.UrbanCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 오피스텔·도시형 등 공고의 경쟁률을 주택형 순으로 조회합니다.
 * 테이블: TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET, 키: AnnouncementModelId. 스케줄러가 청약홈 결과를 모아 넣고 API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. 호출: AnnouncementCompetitionService.
 */
public interface UrbanCompetitionRepository extends JpaRepository<UrbanCompetitionEntity, UrbanCompetitionId> {
    // 메서드 이름으로 쿼리를 만드는 파생 쿼리. 한 공고의 행을 모델번호 순, 같은 주택형은 거주자 우선(Y)을 먼저 둔다.
    List<UrbanCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscResidntPriorAtDesc(
            String houseManageNo, String pblancNo
    );
}
