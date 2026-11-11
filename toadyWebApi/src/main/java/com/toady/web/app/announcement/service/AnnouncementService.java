package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.CompetitionDataType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.dto.AnnouncementSuggestionResponse;
import com.toady.web.app.announcement.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 공고 조회의 공개 진입점. 검색, 상세, 경쟁률 처리는 전담 서비스에 위임합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {
    private final AnnouncementSearchService searchService;
    private final AnnouncementDetailService detailService;
    private final AnnouncementCompetitionService competitionService;

    public Page<?> search(AnnouncementType type, AnnouncementSearch search) {
        return searchService.search(type, search);
    }

    public Page<AnnouncementListItem> searchAll(AnnouncementSearch search, List<String> requestedTypes) {
        return searchService.searchAll(search, requestedTypes);
    }

    public List<AnnouncementSuggestionResponse> suggestions(String keyword, int limit) {
        return searchService.suggestions(keyword, limit);
    }

    public Map<String, Object> getDetail(AnnouncementType type, String houseManageNo, String pblancNo) {
        return detailService.getDetail(type, houseManageNo, pblancNo);
    }

    public boolean exists(AnnouncementType type, String houseManageNo, String pblancNo) {
        return detailService.exists(type, houseManageNo, pblancNo);
    }

    public List<?> getCompetitionRates(CompetitionDataType type, String houseManageNo, String pblancNo) {
        return competitionService.getCompetitionRates(type, houseManageNo, pblancNo);
    }

    public List<AptScoreEntity> getAptScores(String houseManageNo, String pblancNo) {
        return competitionService.getAptScores(houseManageNo, pblancNo);
    }

    public List<AptSpecialSupplyStatusEntity> getAptSpecialSupplyStatus(String houseManageNo, String pblancNo) {
        return competitionService.getAptSpecialSupplyStatus(houseManageNo, pblancNo);
    }
}
