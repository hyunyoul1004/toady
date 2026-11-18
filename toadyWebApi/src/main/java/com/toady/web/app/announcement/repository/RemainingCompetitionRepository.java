package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.RemainingCompetitionEntity;
import com.toady.web.app.announcement.entity.id.RemainingCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 잔여세대 공고의 주택형·사전/사후 구분별 경쟁률을 조회합니다.
 * 테이블: TB_REMNDR_LTTOT_PBLANC_CMPET, 키: RemainingCompetitionId. 스케줄러가 청약홈 결과를 모아 넣고 API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. 호출: AnnouncementCompetitionService.
 */
public interface RemainingCompetitionRepository
        extends JpaRepository<RemainingCompetitionEntity, RemainingCompetitionId> {
    // 메서드 이름으로 쿼리를 만드는 파생 쿼리. 한 공고의 행을 주택형 → 잔여세대 공고 구분 순으로 정렬해 돌려준다.
    List<RemainingCompetitionEntity>
    findByHouseManageNoAndPblancNoOrderByHouseTyAscRemndrHshldPblancTycdAsc(
            String houseManageNo, String pblancNo
    );
}
