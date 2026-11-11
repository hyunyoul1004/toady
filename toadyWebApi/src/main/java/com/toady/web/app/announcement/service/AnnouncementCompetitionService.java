package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.CompetitionDataType;
import com.toady.web.app.announcement.entity.*;
import com.toady.web.app.announcement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
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

    public List<AptScoreEntity> getAptScores(String houseManageNo, String pblancNo) {
        return aptScoreRepository.findByHouseManageNoAndPblancNoOrderByModelNoAscResideSecdAsc(
                houseManageNo, pblancNo
        );
    }

    public List<AptSpecialSupplyStatusEntity> getAptSpecialSupplyStatus(
            String houseManageNo, String pblancNo
    ) {
        return aptSpecialSupplyStatusRepository
                .findByHouseManageNoAndPblancNoOrderByHouseTyAsc(houseManageNo, pblancNo);
    }

}
