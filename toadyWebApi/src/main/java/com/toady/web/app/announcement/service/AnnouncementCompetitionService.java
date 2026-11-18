package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.CompetitionDataType;
import com.toady.web.app.announcement.entity.*;
import com.toady.web.app.announcement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공고의 경쟁률·당첨가점·특별공급 신청현황을 읽는다.
 * 흐름: AnnouncementController → AnnouncementService → 이 클래스 → 각 경쟁률 저장소.
 * 테이블: TB_APT_LTTOT_PBLANC_CMPET, TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET, TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET,
 *        TB_CANC_RESPL_LTTOT_PBLANC_CMPET, TB_REMNDR_LTTOT_PBLANC_CMPET, TB_OPT_LTTOT_PBLANC_CMPET,
 *        TB_APT_LTTOT_PBLANC_SCORE, TB_APT_SPSPLY_REQST_STUS (모두 읽기만 한다).
 */
// @Service: 스프링 빈으로 등록한다.
// @RequiredArgsConstructor(Lombok): final 필드(저장소들)를 받는 생성자를 만들어 생성자 주입이 되게 한다.
@Service
@RequiredArgsConstructor
// @Transactional(readOnly = true): 모든 public 메서드를 조회 전용 트랜잭션으로 묶는다.
// 변경 감지를 하지 않아 가볍고, 실수로 바꾼 값이 DB에 저장되지 않는다.
@Transactional(readOnly = true)
public class AnnouncementCompetitionService {
    private final AptCompetitionRepository aptCompetitionRepository;
    private final UrbanCompetitionRepository urbanCompetitionRepository;
    private final PublicRentalCompetitionRepository publicRentalCompetitionRepository;
    private final CancelResupplyCompetitionRepository cancelResupplyCompetitionRepository;
    private final RemainingCompetitionRepository remainingCompetitionRepository;
    private final OptionalCompetitionRepository optionalCompetitionRepository;
    private final AptScoreRepository aptScoreRepository;
    private final AptSpecialSupplyStatusRepository aptSpecialSupplyStatusRepository;


    /**
     * 유형에 맞는 경쟁률 테이블을 골라 읽는다. 유형마다 엔티티 모양이 달라 List<?>로 돌려준다.
     * enum 전체를 다루는 switch 식이라, 유형이 새로 생기면 여기서 컴파일 오류가 나 놓치지 않는다.
     */
    public List<?> getCompetitionRates(CompetitionDataType type, String houseManageNo, String pblancNo) {
        return switch (type) {
            case APT -> aptCompetitionRepository
                    .findByHouseManageNoAndPblancNoOrderByModelNoAscSubscrptRankCodeAscResideSecdAsc(
                            houseManageNo, pblancNo
                    );
            case URBAN -> urbanCompetitionRepository
                    .findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            case PUBLIC_RENTAL -> publicRentalCompetitionRepository
                    .findByHouseManageNoAndPblancNoOrderByModelNoAscSpsplyKndCodeAsc(
                            houseManageNo, pblancNo
                    );
            case CANCEL_RESUPPLY -> cancelResupplyCompetitionRepository
                    .findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            case REMAINING -> remainingCompetitionRepository
                    .findByHouseManageNoAndPblancNoOrderByHouseTyAscRemndrHshldPblancTycdAsc(
                            houseManageNo, pblancNo
                    );
            case OPTIONAL -> optionalCompetitionRepository
                    .findByHouseManageNoAndPblancNoOrderByHouseTyAsc(houseManageNo, pblancNo);
        };
    }

    /** APT 공고의 주택형·거주지역별 당첨가점. */
    public List<AptScoreEntity> getAptScores(String houseManageNo, String pblancNo) {
        return aptScoreRepository.findByHouseManageNoAndPblancNoOrderByModelNoAscResideSecdAsc(
                houseManageNo, pblancNo
        );
    }

    /** APT 공고의 주택형별 특별공급 신청현황. */
    public List<AptSpecialSupplyStatusEntity> getAptSpecialSupplyStatus(
            String houseManageNo, String pblancNo
    ) {
        return aptSpecialSupplyStatusRepository
                .findByHouseManageNoAndPblancNoOrderByHouseTyAsc(houseManageNo, pblancNo);
    }

}
