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

/**
 * 공고 조회의 공개 진입점. 검색, 상세, 경쟁률 처리는 전담 서비스에 위임합니다.
 * 흐름: AnnouncementController·AnnouncementCommentService·PlannerSummary 등 → 이 클래스
 *      → AnnouncementSearchService / AnnouncementDetailService / AnnouncementCompetitionService → 저장소.
 * 바깥 코드는 이 클래스 하나만 알면 되도록 창구를 하나로 모아 두었다.
 */
// @Service: 비즈니스 로직을 담는 스프링 빈으로 등록한다.
@Service
// @RequiredArgsConstructor(Lombok): final 필드를 받는 생성자를 만든다. 스프링이 이 생성자로 빈을 넣어 준다.
@RequiredArgsConstructor
// @Transactional(readOnly = true): 모든 public 메서드를 조회 전용 트랜잭션으로 묶는다.
// 변경 감지를 하지 않아 가볍고, 실수로 바꾼 값이 DB에 저장되지 않는다.
@Transactional(readOnly = true)
public class AnnouncementService {
    private final AnnouncementSearchService searchService;
    private final AnnouncementDetailService detailService;
    private final AnnouncementCompetitionService competitionService;

    /** 유형 하나의 공고 목록(엔티티 페이지). */
    public Page<?> search(AnnouncementType type, AnnouncementSearch search) {
        return searchService.search(type, search);
    }

    /** 여러 유형을 합친 통합 목록. */
    public Page<AnnouncementListItem> searchAll(AnnouncementSearch search, List<String> requestedTypes) {
        return searchService.searchAll(search, requestedTypes);
    }

    /** 검색창 자동완성 후보. */
    public List<AnnouncementSuggestionResponse> suggestions(String keyword, int limit) {
        return searchService.suggestions(keyword, limit);
    }

    /** 공고 상세(공고·주택형·운영자 안내). 없으면 EntityNotFoundException. */
    public Map<String, Object> getDetail(AnnouncementType type, String houseManageNo, String pblancNo) {
        return detailService.getDetail(type, houseManageNo, pblancNo);
    }

    /** 공고가 있는지만 확인한다. 댓글 쓰기 전에 쓴다. */
    public boolean exists(AnnouncementType type, String houseManageNo, String pblancNo) {
        return detailService.exists(type, houseManageNo, pblancNo);
    }

    /** 유형별 경쟁률 행 목록. */
    public List<?> getCompetitionRates(CompetitionDataType type, String houseManageNo, String pblancNo) {
        return competitionService.getCompetitionRates(type, houseManageNo, pblancNo);
    }

    /** APT 당첨가점. */
    public List<AptScoreEntity> getAptScores(String houseManageNo, String pblancNo) {
        return competitionService.getAptScores(houseManageNo, pblancNo);
    }

    /** APT 특별공급 신청현황. */
    public List<AptSpecialSupplyStatusEntity> getAptSpecialSupplyStatus(String houseManageNo, String pblancNo) {
        return competitionService.getAptSpecialSupplyStatus(houseManageNo, pblancNo);
    }
}
