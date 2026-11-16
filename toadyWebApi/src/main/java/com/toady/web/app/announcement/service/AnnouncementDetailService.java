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

@Service
@RequiredArgsConstructor
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

    public boolean exists(AnnouncementType type, String houseManageNo, String pblancNo) {
        return switch (type) {
            case APT -> aptRepository.existsById(id(houseManageNo, pblancNo));
            case URBAN -> urbanRepository.existsById(id(houseManageNo, pblancNo));
            case REMAINING -> remainingRepository.existsById(id(houseManageNo, pblancNo));
            case PUBLIC_RENTAL -> publicRentalRepository.existsById(id(houseManageNo, pblancNo));
            case OPTIONAL -> optionalRepository.existsById(id(houseManageNo, pblancNo));
        };
    }

    private static AnnouncementId id(String houseManageNo, String pblancNo) {
        return new AnnouncementId(houseManageNo, pblancNo);
    }
}
