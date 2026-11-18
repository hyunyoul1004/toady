package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AptCompetitionEntity;
import com.toady.web.app.announcement.entity.id.AptCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * APT 공고의 경쟁률을 주택형·순위·거주지역 순으로 조회합니다.
 * 테이블: TB_APT_LTTOT_PBLANC_CMPET, 키: AptCompetitionId. 스케줄러가 청약홈 결과를 모아 넣고 API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. 호출: AnnouncementCompetitionService.
 */
public interface AptCompetitionRepository extends JpaRepository<AptCompetitionEntity, AptCompetitionId> {
    // 메서드 이름으로 쿼리를 만드는 파생 쿼리. 한 공고의 행을 주택형 → 순위 → 거주지역 순으로 정렬해 돌려준다.
    List<AptCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscSubscrptRankCodeAscResideSecdAsc(
            String houseManageNo, String pblancNo
    );
}
