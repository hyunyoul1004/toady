package com.toady.web.app.announcement.repository;

import com.toady.web.app.announcement.entity.PublicRentalCompetitionEntity;
import com.toady.web.app.announcement.entity.id.PublicRentalCompetitionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 공공지원 민간임대 공고의 공급유형별 경쟁률을 조회합니다.
 * 테이블: TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET, 키: PublicRentalCompetitionId. 스케줄러가 청약홈 결과를 모아 넣고 API는 읽기만 한다.
 * 인터페이스만 선언하면 Spring Data JPA가 구현체를 만들어 준다. 호출: AnnouncementCompetitionService.
 */
public interface PublicRentalCompetitionRepository
        extends JpaRepository<PublicRentalCompetitionEntity, PublicRentalCompetitionId> {
    // 메서드 이름으로 쿼리를 만드는 파생 쿼리. 한 공고의 행을 모델번호 → 공급유형 코드 순으로 정렬해 돌려준다.
    List<PublicRentalCompetitionEntity> findByHouseManageNoAndPblancNoOrderByModelNoAscSpsplyKndCodeAsc(
            String houseManageNo, String pblancNo
    );
}
