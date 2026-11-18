package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.AptScoreEntity;
import com.toady.web.app.announcement.entity.id.AptScoreId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * APT 공고의 주택형·거주지역별 당첨가점을 조회합니다.
 * 테이블: TB_APT_LTTOT_PBLANC_SCORE, 키: AptScoreId. 스케줄러가 청약홈 결과를 모아 넣고 API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. 호출: AnnouncementCompetitionService.
 */
public interface AptScoreRepository extends JpaRepository<AptScoreEntity, AptScoreId> {
    // 메서드 이름으로 쿼리를 만드는 파생 쿼리. 한 공고의 행을 주택형 → 거주지역 순으로 정렬해 돌려준다.
    List<AptScoreEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscResideSecdAsc(
            String houseManageNo, String pblancNo
    );
}
