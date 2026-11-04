package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.CompetitionDataType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.entity.*;
import com.toady.web.app.announcement.entity.id.AnnouncementId;
import com.toady.web.app.announcement.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** 공고 유형별 조회와 통합 검색을 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {
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
    private final AptCompetitionRepository aptCompetitionRepository;
    private final UrbanCompetitionRepository urbanCompetitionRepository;
    private final PublicRentalCompetitionRepository publicRentalCompetitionRepository;
    private final CancelResupplyCompetitionRepository cancelResupplyCompetitionRepository;
    private final RemainingCompetitionRepository remainingCompetitionRepository;
    private final OptionalCompetitionRepository optionalCompetitionRepository;
    private final AptScoreRepository aptScoreRepository;
    private final AptSpecialSupplyStatusRepository aptSpecialSupplyStatusRepository;

    public Page<?> search(AnnouncementType type, AnnouncementSearch search) {
        validateSearch(search);
        Pageable pageable = PageRequest.of(
                search.page(),
                search.size(),
                Sort.by(Sort.Direction.DESC, receptionStartDateProperty(type))
        );
        return switch (type) {
            case APT -> aptRepository.findAll(searchSpecification(search), pageable);
            case URBAN -> urbanRepository.findAll(searchSpecification(search), pageable);
            case REMAINING -> remainingRepository.findAll(searchSpecification(search), pageable);
            case PUBLIC_RENTAL -> publicRentalRepository.findAll(searchSpecification(search), pageable);
            case OPTIONAL -> optionalRepository.findAll(searchSpecification(search), pageable);
        };
    }

    /** 선택한 유형의 공고를 접수 시작일 기준으로 통합 페이징합니다. */
    public Page<AnnouncementListItem> searchAll(AnnouncementSearch search, List<String> requestedTypes) {
        validateSearch(search);
        Set<AnnouncementType> types = selectedTypes(requestedTypes);

        long requiredCount = ((long) search.page() + 1) * search.size();
        if (requiredCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("요청한 페이지 범위가 너무 큽니다.");
        }

        Specification<AptAnnouncementEntity> aptSpec = searchSpecification(search);
        Specification<UrbanAnnouncementEntity> urbanSpec = searchSpecification(search);
        Specification<RemainingAnnouncementEntity> remainingSpec = searchSpecification(search);
        Specification<PublicRentalAnnouncementEntity> publicRentalSpec = searchSpecification(search);
        Specification<OptionalAnnouncementEntity> optionalSpec = searchSpecification(search);

        List<AnnouncementCandidate> candidates = new ArrayList<>();
        long totalElements = 0;

        if (types.contains(AnnouncementType.APT)) {
            Page<AptAnnouncementEntity> page = aptRepository.findAll(
                    aptSpec, candidatePage(requiredCount, AnnouncementType.APT)
            );
            totalElements += page.getTotalElements();
            page.forEach(item -> candidates.add(new AnnouncementCandidate(
                    new AnnouncementListItem(
                            AnnouncementType.APT.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                            item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                            item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                            item.getHssplyAdres(), item.getHssplyZip(),
                            item.getRceptBgnde(), item.getRceptEndde(),
                            item.getGnrlRnk1CrspareaRcptde(), item.getGnrlRnk1CrspareaEndde(),
                            item.getGnrlRnk1EtcGgRcptde(), item.getGnrlRnk1EtcGgEndde(),
                            item.getGnrlRnk1EtcAreaRcptde(), item.getGnrlRnk1EtcAreaEndde(),
                            item.getGnrlRnk2CrspareaRcptde(), item.getGnrlRnk2CrspareaEndde(),
                            item.getGnrlRnk2EtcGgRcptde(), item.getGnrlRnk2EtcGgEndde(),
                            item.getGnrlRnk2EtcAreaRcptde(), item.getGnrlRnk2EtcAreaEndde(),
                            item.getTotSuplyHshldco()
                    ),
                    item.getRceptBgnde()
            )));
        }
        if (types.contains(AnnouncementType.URBAN)) {
            Page<UrbanAnnouncementEntity> page = urbanRepository.findAll(
                    urbanSpec, candidatePage(requiredCount, AnnouncementType.URBAN)
            );
            totalElements += page.getTotalElements();
            page.forEach(item -> candidates.add(new AnnouncementCandidate(
                    new AnnouncementListItem(
                            AnnouncementType.URBAN.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                            item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                            item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                            item.getHssplyHshldco(), item.getHssplyZip(),
                            item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                            null, null, null, null, null, null, null, null, null, null, null, null,
                            item.getTotSuplyHshldco()
                    ),
                    item.getSubscrptRceptBgnde()
            )));
        }
        if (types.contains(AnnouncementType.REMAINING)) {
            Page<RemainingAnnouncementEntity> page =
                    remainingRepository.findAll(
                            remainingSpec, candidatePage(requiredCount, AnnouncementType.REMAINING)
                    );
            totalElements += page.getTotalElements();
            page.forEach(item -> candidates.add(new AnnouncementCandidate(
                    new AnnouncementListItem(
                            AnnouncementType.REMAINING.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                            item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                            item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                            item.getHssplyAdres(), item.getHssplyZip(),
                            item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                            null, null, null, null, null, null, null, null, null, null, null, null,
                            item.getTotSuplyHshldco()
                    ),
                    item.getSubscrptRceptBgnde()
            )));
        }
        if (types.contains(AnnouncementType.PUBLIC_RENTAL)) {
            Page<PublicRentalAnnouncementEntity> page =
                    publicRentalRepository.findAll(
                            publicRentalSpec, candidatePage(requiredCount, AnnouncementType.PUBLIC_RENTAL)
                    );
            totalElements += page.getTotalElements();
            page.forEach(item -> candidates.add(new AnnouncementCandidate(
                    new AnnouncementListItem(
                            AnnouncementType.PUBLIC_RENTAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                            item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                            item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                            item.getHssplyAdres(), item.getHssplyZip(),
                            item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                            null, null, null, null, null, null, null, null, null, null, null, null,
                            item.getTotSuplyHshldco()
                    ),
                    item.getSubscrptRceptBgnde()
            )));
        }
        if (types.contains(AnnouncementType.OPTIONAL)) {
            Page<OptionalAnnouncementEntity> page =
                    optionalRepository.findAll(
                            optionalSpec, candidatePage(requiredCount, AnnouncementType.OPTIONAL)
                    );
            totalElements += page.getTotalElements();
            page.forEach(item -> candidates.add(new AnnouncementCandidate(
                    new AnnouncementListItem(
                            AnnouncementType.OPTIONAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                            item.getHouseNm(), item.getHouseSecd(), item.getHouseSecdNm(),
                            item.getSubscrptAreaCode(), item.getSubscrptAreaCodeNm(),
                            item.getHssplyAdres(), item.getHssplyZip(),
                            item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde(),
                            null, null, null, null, null, null, null, null, null, null, null, null,
                            item.getTotSuplyHshldco()
                    ),
                    item.getSubscrptRceptBgnde()
            )));
        }

        candidates.sort(Comparator
                .comparing(
                        (AnnouncementCandidate candidate) -> normalizedDate(candidate.receptionStartDate()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(candidate -> candidate.item().type())
                .thenComparing(candidate -> candidate.item().houseManageNo())
                .thenComparing(candidate -> candidate.item().pblancNo()));

        int fromIndex = (int) Math.min((long) search.page() * search.size(), candidates.size());
        int toIndex = Math.min(fromIndex + search.size(), candidates.size());
        Pageable resultPage = PageRequest.of(search.page(), search.size());
        List<AnnouncementListItem> content = candidates.subList(fromIndex, toIndex).stream()
                .map(AnnouncementCandidate::item)
                .toList();
        return new PageImpl<>(content, resultPage, totalElements);
    }

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

    private static <T> Specification<T> searchSpecification(AnnouncementSearch search) {
        return (root, query, builder) -> {
            var predicates = builder.conjunction();
            if (search.regionCode() != null && !search.regionCode().isBlank()) {
                predicates = builder.and(predicates, builder.equal(root.get("subscrptAreaCode"), search.regionCode()));
            }
            if (search.keyword() != null && !search.keyword().isBlank()) {
                predicates = builder.and(
                        predicates,
                        builder.like(builder.lower(root.get("houseNm")), "%" + search.keyword().trim().toLowerCase() + "%")
                );
            }

            // 날짜 조건은 모집공고일이 아닌 청약접수 시작일을 대상으로 적용합니다.
            if (search.startDate() != null) {
                String compactDate = search.startDate().format(DateTimeFormatter.BASIC_ISO_DATE);
                predicates = builder.and(
                        predicates,
                        builder.greaterThanOrEqualTo(normalizedReceptionStartDate(root, builder), compactDate)
                );
            }
            if (search.endDate() != null) {
                String compactDate = search.endDate().format(DateTimeFormatter.BASIC_ISO_DATE);
                predicates = builder.and(
                        predicates,
                        builder.lessThanOrEqualTo(normalizedReceptionStartDate(root, builder), compactDate)
                );
            }
            return predicates;
        };
    }

    private static <T> jakarta.persistence.criteria.Expression<String> normalizedReceptionStartDate(
            jakarta.persistence.criteria.Root<T> root,
            jakarta.persistence.criteria.CriteriaBuilder builder
    ) {
        // APT와 나머지 유형의 컬럼명이 다르므로 엔티티 유형에 맞는 청약접수 시작일을 선택합니다.
        String receptionStartDateField = root.getJavaType() == AptAnnouncementEntity.class
                ? "rceptBgnde"
                : "subscrptRceptBgnde";
        return builder.function(
                "replace",
                String.class,
                root.get(receptionStartDateField),
                builder.literal("-"),
                builder.literal("")
        );
    }

    private static void validateSearch(AnnouncementSearch search) {
        // 시작일이 종료일보다 뒤면 검색 전에 요청을 거부합니다.
        if (search.startDate() != null && search.endDate() != null
                && search.startDate().isAfter(search.endDate())) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private static Set<AnnouncementType> selectedTypes(List<String> requestedTypes) {
        // 유형을 생략하면 전체 유형을 대상으로 하고, 콤마 구분 입력도 개별 유형으로 분리합니다.
        if (requestedTypes == null || requestedTypes.isEmpty()) {
            return EnumSet.allOf(AnnouncementType.class);
        }
        Set<AnnouncementType> selected = EnumSet.noneOf(AnnouncementType.class);
        requestedTypes.stream()
                .flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(AnnouncementType::fromPath)
                .forEach(selected::add);
        if (selected.isEmpty()) {
            return EnumSet.allOf(AnnouncementType.class);
        }
        return selected;
    }

    private static String normalizedDate(String date) {
        if (date == null) {
            return null;
        }
        // 유형별 접수일 형식을 통일해 날짜순으로 비교합니다.
        String digits = date.replaceAll("\\D", "");
        return digits.length() < 8 ? null : digits.substring(0, 8);
    }

    private static Pageable candidatePage(long count, AnnouncementType type) {
        return PageRequest.of(
                0,
                Math.toIntExact(count),
                Sort.by(Sort.Direction.DESC, receptionStartDateProperty(type))
        );
    }

    private static String receptionStartDateProperty(AnnouncementType type) {
        return type == AnnouncementType.APT ? "rceptBgnde" : "subscrptRceptBgnde";
    }

    private static AnnouncementId id(String houseManageNo, String pblancNo) {
        return new AnnouncementId(houseManageNo, pblancNo);
    }

    private record AnnouncementCandidate(AnnouncementListItem item, String receptionStartDate) {
    }
}
