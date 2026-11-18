package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.entity.*;
import com.toady.web.app.announcement.entity.id.AnnouncementId;
import com.toady.web.app.announcement.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 공고 상세(공고 정보 + 주택형 목록 + 운영자 안내)와 공고 존재 여부를 조회한다.
 * 흐름: AnnouncementController → AnnouncementService → 이 클래스 → 유형별 공고·주택형 저장소.
 * 테이블: 유형별 *_LTTOT_PBLANC_DETAIL·*_LTTOT_PBLANC_MDL(읽기). 운영자 안내는 AnnouncementOverrides에서 읽는다.
 */
// @Service: 스프링 빈으로 등록한다.
// @RequiredArgsConstructor(Lombok): final 필드를 받는 생성자를 만들어 생성자 주입이 되게 한다.
@Service
@RequiredArgsConstructor
// @Transactional(readOnly = true): 모든 public 메서드를 조회 전용 트랜잭션으로 묶는다.
// 변경 감지를 하지 않아 가볍고, 실수로 바꾼 값이 DB에 저장되지 않는다.
@Transactional(readOnly = true)
public class AnnouncementDetailService {
    private final AptAnnouncementRepository aptRepository;
    private final AptAnnouncementModelRepository aptModelRepository;
    private final UrbanAnnouncementRepository urbanRepository;
    private final UrbanAnnouncementModelRepository urbanModelRepository;
    private final RemainingAnnouncementRepository remainingRepository;
    private final RemainingAnnouncementModelRepository remainingModelRepository;
    private final PublicRentalAnnouncementRepository publicRentalRepository;
    private final PublicRentalAnnouncementModelRepository publicRentalModelRepository;
    private final OptionalAnnouncementRepository optionalRepository;
    private final OptionalAnnouncementModelRepository optionalModelRepository;
    private final com.toady.web.app.operation.AnnouncementOverrides overrides;

    /**
     * 공고와 주택형을 읽어 Map에 담아 돌려준다. 공고가 없으면 EntityNotFoundException(→ 404).
     * 유형마다 엔티티 타입이 달라 Object로 받고, 엔티티를 그대로 JSON으로 내보낸다.
     * Map을 돌려주는 것은 컨트롤러가 commentCount를 덧붙일 수 있게 하려는 것이다.
     */
    public Map<String, Object> getDetail(AnnouncementType type, String houseManageNo, String pblancNo) {
        Object announcement;
        Object models;
        switch (type) {
            case APT -> {
                announcement = aptRepository.findById(id(houseManageNo, pblancNo))
                        .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));
                models = aptModelRepository.findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            }
            case URBAN -> {
                announcement = urbanRepository.findById(id(houseManageNo, pblancNo))
                        .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));
                models = urbanModelRepository.findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            }
            case REMAINING -> {
                announcement = remainingRepository.findById(id(houseManageNo, pblancNo))
                        .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));
                models = remainingModelRepository.findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            }
            case PUBLIC_RENTAL -> {
                announcement = publicRentalRepository.findById(id(houseManageNo, pblancNo))
                        .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));
                models = publicRentalModelRepository.findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            }
            case OPTIONAL -> {
                announcement = optionalRepository.findById(id(houseManageNo, pblancNo))
                        .orElseThrow(() -> new EntityNotFoundException("공고를 찾을 수 없습니다."));
                models = optionalModelRepository.findByHouseManageNoAndPblancNoOrderByModelNoAsc(houseManageNo, pblancNo);
            }
            default -> throw new IllegalStateException("지원하지 않는 공고 유형입니다.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("announcement", announcement);
        result.put("models", models);
        // 운영자 안내(정정·취소 등)와 숨김 여부. 숨긴 공고도 주소로 열면 안내와 함께 보여 준다.
        var override = overrides.get(type.name(), houseManageNo, pblancNo);
        if (override != null) {
            Map<String, Object> notice = new LinkedHashMap<>();
            notice.put("hidden", override.hidden());
            notice.put("message", override.notice());
            notice.put("locationAddress", override.locationAddress());
            result.put("operatorNotice", notice);
        }
        return result;
    }

    /** 키로 존재만 확인한다(existsById). 엔티티 전체를 읽지 않아 가볍다. */
    public boolean exists(AnnouncementType type, String houseManageNo, String pblancNo) {
        return switch (type) {
            case APT -> aptRepository.existsById(id(houseManageNo, pblancNo));
            case URBAN -> urbanRepository.existsById(id(houseManageNo, pblancNo));
            case REMAINING -> remainingRepository.existsById(id(houseManageNo, pblancNo));
            case PUBLIC_RENTAL -> publicRentalRepository.existsById(id(houseManageNo, pblancNo));
            case OPTIONAL -> optionalRepository.existsById(id(houseManageNo, pblancNo));
        };
    }

    // 복합키 객체를 만든다. findById·existsById에 키 하나로 넘기기 위해서다.
    private static AnnouncementId id(String houseManageNo, String pblancNo) {
        return new AnnouncementId(houseManageNo, pblancNo);
    }
}
