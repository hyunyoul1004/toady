package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.dto.AnnouncementSuggestionResponse;
import com.toady.web.app.announcement.entity.*;
import com.toady.web.app.announcement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementSearchService {
    private final AptAnnouncementRepository aptRepository;
    private final UrbanAnnouncementRepository urbanRepository;
    private final RemainingAnnouncementRepository remainingRepository;
    private final PublicRentalAnnouncementRepository publicRentalRepository;
    private final OptionalAnnouncementRepository optionalRepository;

    public Page<?> search(AnnouncementType type, AnnouncementSearch search) {
        validateSearch(search);
        Pageable pageable = PageRequest.of(
                search.page(),
                search.size()
        );
        return switch (type) {
            case APT -> aptRepository.findAll(searchSpecification(search), pageable);
            case URBAN -> urbanRepository.findAll(searchSpecification(search), pageable);
            case REMAINING -> remainingRepository.findAll(searchSpecification(search), pageable);
            case PUBLIC_RENTAL -> publicRentalRepository.findAll(searchSpecification(search), pageable);
            case OPTIONAL -> optionalRepository.findAll(searchSpecification(search), pageable);
        };
    }

    /** 전체 공고 유형에서 주택명 또는 주소가 일치하는 자동완성 후보를 접수일 최신순으로 조회합니다. */
    public List<AnnouncementSuggestionResponse> suggestions(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<AnnouncementSuggestionResponse> suggestions = new ArrayList<>();
        suggestions.addAll(findSuggestions(
                aptRepository, "rceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.APT.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getRceptBgnde(), item.getRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                urbanRepository, "subscrptRceptBgnde", "hssplyHshldco",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.URBAN.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyHshldco(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                remainingRepository, "subscrptRceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.REMAINING.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                publicRentalRepository, "subscrptRceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.PUBLIC_RENTAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                optionalRepository, "subscrptRceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.OPTIONAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));

        return suggestions.stream()
                .sorted(Comparator
                        .comparing(
                                (AnnouncementSuggestionResponse item) -> normalizedDate(item.rceptBgnde()),
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                        .thenComparing(AnnouncementSuggestionResponse::type)
                        .thenComparing(AnnouncementSuggestionResponse::houseManageNo)
                        .thenComparing(AnnouncementSuggestionResponse::pblancNo))
                .limit(limit)
                .toList();
    }

    private static <T> List<AnnouncementSuggestionResponse> findSuggestions(
            JpaSpecificationExecutor<T> repository,
            String receptionStartDateProperty,
            String addressProperty,
            String keyword,
            int limit,
            Function<T, AnnouncementSuggestionResponse> mapper
    ) {
        String pattern = "%" + keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
        Specification<T> specification = (root, query, builder) -> {
            var nameMatches = builder.like(
                    builder.lower(root.get("houseNm")), pattern, '\\'
            );
            var addressMatches = builder.like(
                    builder.lower(root.get(addressProperty)), pattern, '\\'
            );
            var date = builder.function("replace", String.class,
                    root.get(receptionStartDateProperty), builder.literal("-"), builder.literal(""));
            query.orderBy(
                    builder.asc(builder.<Integer>selectCase().when(builder.isNull(date), 1).otherwise(0)),
                    builder.desc(date), builder.asc(root.get("houseManageNo")), builder.asc(root.get("pblancNo"))
            );
            return builder.or(nameMatches, addressMatches);
        };
        return repository.findBy(specification, query -> query.limit(limit).all())
                .stream()
                .map(mapper)
                .toList();
    }

    /** 선택한 유형의 공고를 접수 시작일 기준으로 통합 페이징합니다. */
    public Page<AnnouncementListItem> searchAll(AnnouncementSearch search, List<String> requestedTypes) {
        validateSearch(search);
        Set<AnnouncementType> types = selectedTypes(requestedTypes);

        long requiredCount = ((long) search.page() + 1) * search.size();
        // 유형별 선행 조회가 무제한으로 커지지 않도록 통합 탐색 범위를 제한한다.
        if (requiredCount > 10_000) {
            throw new IllegalArgumentException("통합 검색은 (page + 1) × size가 10000 이하인 범위에서 조회할 수 있습니다.");
        }

        List<AnnouncementListItem> candidates = new ArrayList<>();
        long totalElements = 0;
        for (AnnouncementType type : types) {
            Pageable pageable = candidatePage(requiredCount);
            Page<AnnouncementListItem> page = switch (type) {
                case APT -> aptRepository.findAll(
                        AnnouncementSearchService.<AptAnnouncementEntity>searchSpecification(search), pageable
                ).map(AnnouncementListMapper::toListItem);
                case URBAN -> urbanRepository.findAll(
                        AnnouncementSearchService.<UrbanAnnouncementEntity>searchSpecification(search), pageable
                ).map(AnnouncementListMapper::toListItem);
                case REMAINING -> remainingRepository.findAll(
                        AnnouncementSearchService.<RemainingAnnouncementEntity>searchSpecification(search), pageable
                ).map(AnnouncementListMapper::toListItem);
                case PUBLIC_RENTAL -> publicRentalRepository.findAll(
                        AnnouncementSearchService.<PublicRentalAnnouncementEntity>searchSpecification(search), pageable
                ).map(AnnouncementListMapper::toListItem);
                case OPTIONAL -> optionalRepository.findAll(
                        AnnouncementSearchService.<OptionalAnnouncementEntity>searchSpecification(search), pageable
                ).map(AnnouncementListMapper::toListItem);
            };
            totalElements += page.getTotalElements();
            candidates.addAll(page.getContent());
        }

        candidates.sort(Comparator
                .comparing(
                        (AnnouncementListItem item) -> normalizedDate(item.rceptBgnde()),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(AnnouncementListItem::type)
                .thenComparing(AnnouncementListItem::houseManageNo)
                .thenComparing(AnnouncementListItem::pblancNo));

        int fromIndex = (int) Math.min((long) search.page() * search.size(), candidates.size());
        int toIndex = Math.min(fromIndex + search.size(), candidates.size());
        Pageable resultPage = PageRequest.of(search.page(), search.size());
        List<AnnouncementListItem> content = List.copyOf(candidates.subList(fromIndex, toIndex));
        return new PageImpl<>(content, resultPage, totalElements);
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
            // Apply normalized ordering before limiting candidates; also keep page boundaries stable.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var date = normalizedReceptionStartDate(root, builder);
                query.orderBy(
                        builder.asc(builder.<Integer>selectCase().when(builder.isNull(date), 1).otherwise(0)),
                        builder.desc(date), builder.asc(root.get("houseManageNo")), builder.asc(root.get("pblancNo"))
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

    private static Pageable candidatePage(long count) {
        return PageRequest.of(0, Math.toIntExact(count));
    }
}
