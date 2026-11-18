package com.toady.web.app.announcement.service;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.dto.AnnouncementSuggestionResponse;
import com.toady.web.app.announcement.entity.*;
import com.toady.web.app.announcement.repository.*;
import com.toady.web.app.planner.LivingAreas;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * 공고 목록·통합 목록·자동완성 검색을 맡는다.
 * 흐름: AnnouncementController → AnnouncementService → 이 클래스 → 유형별 공고 저장소(JpaSpecificationExecutor).
 * 테이블: 유형별 *_LTTOT_PBLANC_DETAIL 5개(읽기). 숨긴 공고 목록은 AnnouncementOverrides, 댓글 수는 AnnouncementCommentCountService.
 *
 * Specification: where 조건을 코드로 조립하는 JPA Criteria API 포장. (root, query, builder) -> 조건 꼴의 람다다.
 * - root: 조회 대상 엔티티(root.get("houseNm") = 그 필드)
 * - query: 쿼리 전체(정렬 orderBy 지정에 쓴다)
 * - builder: equal·like·and·or 같은 조건을 만드는 도구
 * 조건마다 if로 붙였다 뗐다 할 수 있어, 선택 조건이 많은 검색에 맞다.
 */
// @Service: 스프링 빈으로 등록한다.
// @RequiredArgsConstructor(Lombok): final 필드를 받는 생성자를 만들어 생성자 주입이 되게 한다.
// @Transactional(readOnly = true): 조회 전용 트랜잭션. 변경 감지를 하지 않아 가볍고, 실수로 바꾼 값이 저장되지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementSearchService {
    private final AnnouncementCommentCountService commentCounts;
    private final AptAnnouncementRepository aptRepository;
    private final UrbanAnnouncementRepository urbanRepository;
    private final RemainingAnnouncementRepository remainingRepository;
    private final PublicRentalAnnouncementRepository publicRentalRepository;
    private final OptionalAnnouncementRepository optionalRepository;
    private final com.toady.web.app.operation.AnnouncementOverrides overrides;

    /**
     * 운영자가 숨긴 공고(database.sql 26번)를 뺀다. 숨긴 공고는 드물어 (주택관리번호, 공고번호) 조건을 그대로 붙인다.
     * 결과 조건: 원래 조건 and not(번호1 일치) and not(번호2 일치) …
     */
    private <T> Specification<T> visible(AnnouncementType type, Specification<T> specification) {
        var hidden = overrides.hiddenOf(type.name());
        if (hidden.isEmpty()) return specification;
        Specification<T> notHidden = (root, query, builder) -> builder.and(hidden.stream()
                .map(pair -> builder.not(builder.and(builder.equal(root.get("houseManageNo"), pair[0]), builder.equal(root.get("pblancNo"), pair[1]))))
                .toArray(Predicate[]::new));
        return specification.and(notHidden);
    }

    /**
     * 유형 하나의 공고를 검색 조건으로 찾아 페이지로 돌려준다. 항목은 엔티티 그대로다.
     * PageRequest.of(page, size)를 넘기면 Spring Data가 LIMIT/OFFSET 쿼리와 전체 개수 count 쿼리를 함께 실행한다.
     */
    public Page<?> search(AnnouncementType type, AnnouncementSearch search) {
        validateSearch(search);
        // 아주 큰 페이지 번호로 무거운 OFFSET 조회를 반복하지 못하게 탐색 범위를 제한한다.
        if (((long) search.page() + 1) * search.size() > 10_000) {
            throw new IllegalArgumentException("공고 목록은 (page + 1) × size가 10000 이하인 범위에서 조회할 수 있습니다.");
        }
        Pageable pageable = PageRequest.of(
                search.page(),
                search.size()
        );
        return switch (type) {
            case APT -> aptRepository.findAll(visible(AnnouncementType.APT, searchSpecification(search)), pageable);
            case URBAN -> urbanRepository.findAll(visible(AnnouncementType.URBAN, searchSpecification(search)), pageable);
            case REMAINING -> remainingRepository.findAll(visible(AnnouncementType.REMAINING, searchSpecification(search)), pageable);
            case PUBLIC_RENTAL -> publicRentalRepository.findAll(visible(AnnouncementType.PUBLIC_RENTAL, searchSpecification(search)), pageable);
            case OPTIONAL -> optionalRepository.findAll(visible(AnnouncementType.OPTIONAL, searchSpecification(search)), pageable);
        };
    }

    /** 전체 공고 유형에서 주택명 또는 주소가 일치하는 자동완성 후보를 접수일 최신순으로 조회합니다. */
    public List<AnnouncementSuggestionResponse> suggestions(String keyword, int limit) {
        // 한 글자 검색은 5개 테이블 전체를 훑어 비용만 크고 결과가 쓸모없어 받지 않는다(화면도 2자부터 요청).
        if (keyword == null || keyword.isBlank() || keyword.trim().length() < 2) {
            return List.of();
        }

        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        List<AnnouncementSuggestionResponse> suggestions = new ArrayList<>();
        suggestions.addAll(findSuggestions(
                AnnouncementType.APT, aptRepository, "rceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.APT.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getRceptBgnde(), item.getRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                AnnouncementType.URBAN, urbanRepository, "subscrptRceptBgnde", "hssplyHshldco",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.URBAN.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyHshldco(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                AnnouncementType.REMAINING, remainingRepository, "subscrptRceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.REMAINING.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                AnnouncementType.PUBLIC_RENTAL, publicRentalRepository, "subscrptRceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.PUBLIC_RENTAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));
        suggestions.addAll(findSuggestions(
                AnnouncementType.OPTIONAL, optionalRepository, "subscrptRceptBgnde", "hssplyAdres",
                normalizedKeyword, limit,
                item -> new AnnouncementSuggestionResponse(
                        AnnouncementType.OPTIONAL.getPath(), item.getHouseManageNo(), item.getPblancNo(),
                        item.getHouseNm(), item.getHouseSecdNm(), item.getSubscrptAreaCodeNm(),
                        item.getHssplyAdres(), item.getSubscrptRceptBgnde(), item.getSubscrptRceptEndde()
                )
        ));

        // 숨긴 공고는 유형별 조회(findSuggestions → visible)에서 이미 뺐다.
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

    /**
     * 한 유형에서 숨기지 않은 자동완성 후보를 최대 limit개 읽는다. 유형마다 접수일·주소 필드 이름이 달라 이름을 인자로 받는다.
     * findBy(spec, q -> q.limit(n).all()): Spring Data의 Fluent 조회. count 쿼리 없이 앞에서 n개만 가져온다.
     * 정렬: 접수일 없는 공고는 뒤로, 그다음 접수일 최신순, 같으면 번호순.
     */
    private <T> List<AnnouncementSuggestionResponse> findSuggestions(
            AnnouncementType type,
            JpaSpecificationExecutor<T> repository,
            String receptionStartDateProperty,
            String addressProperty,
            String keyword,
            int limit,
            Function<T, AnnouncementSuggestionResponse> mapper
    ) {
        Specification<T> specification = (root, query, builder) -> {
            var matches = new ArrayList<Predicate>();
            for (var matcher : LivingAreas.matchers(keyword)) {
                matches.add(livingAreaPredicate(builder, root, addressProperty, matcher));
            }
            var date = builder.function("replace", String.class,
                    root.get(receptionStartDateProperty), builder.literal("-"), builder.literal(""));
            query.orderBy(
                    builder.asc(builder.<Integer>selectCase().when(builder.isNull(date), 1).otherwise(0)),
                    builder.desc(date), builder.asc(root.get("houseManageNo")), builder.asc(root.get("pblancNo"))
            );
            return builder.or(matches.toArray(Predicate[]::new));
        };
        // 숨긴 공고는 조회 조건에서 빼야 유형마다 limit개를 온전히 채운다(읽은 뒤 거르면 limit보다 적게 나온다).
        return repository.findBy(visible(type, specification), query -> query.limit(limit).all())
                .stream()
                .map(mapper)
                .toList();
    }

    /** 선택한 유형의 공고를 접수 시작일 기준으로 통합 페이징합니다. */
    /**
     * 여러 테이블에 나뉜 공고를 한 목록으로 합쳐 페이지를 만든다. DB 하나의 쿼리로는 합칠 수 없어서:
     * 1) 유형마다 (page+1)×size개를 같은 정렬로 앞에서부터 읽는다. 합친 목록의 해당 페이지는 반드시 이 안에 있다.
     * 2) 메모리에서 합쳐 다시 정렬한 뒤 해당 페이지만 잘라 낸다.
     * 3) 잘라 낸 항목의 댓글 수만 한 번의 쿼리로 채운다.
     * 전체 개수(totalElements)는 유형별 count 합이다.
     */
    public Page<AnnouncementListItem> searchAll(AnnouncementSearch search, List<String> requestedTypes) {
        validateSearch(search);
        Set<AnnouncementType> types = selectedTypes(requestedTypes);

        long requiredCount = ((long) search.page() + 1) * search.size();
        // 유형별 선행 조회가 무제한으로 커지지 않도록 통합 탐색 범위를 제한한다.
        // 유형마다 이 개수만큼 읽어 합치므로 실제 조회량은 유형 수(5)배가 된다. 한 달 캘린더(수백 건)에 충분한 1000으로 둔다.
        if (requiredCount > 1_000) {
            throw new IllegalArgumentException("통합 검색은 (page + 1) × size가 1000 이하인 범위에서 조회할 수 있습니다.");
        }

        List<AnnouncementListItem> candidates = new ArrayList<>();
        long totalElements = 0;
        for (AnnouncementType type : types) {
            Pageable pageable = candidatePage(requiredCount);
            Page<AnnouncementListItem> page = switch (type) {
                case APT -> aptRepository.findAll(
                        visible(AnnouncementType.APT, AnnouncementSearchService.<AptAnnouncementEntity>searchSpecification(search)), pageable
                ).map(AnnouncementListMapper::toListItem);
                case URBAN -> urbanRepository.findAll(
                        visible(AnnouncementType.URBAN, AnnouncementSearchService.<UrbanAnnouncementEntity>searchSpecification(search)), pageable
                ).map(AnnouncementListMapper::toListItem);
                case REMAINING -> remainingRepository.findAll(
                        visible(AnnouncementType.REMAINING, AnnouncementSearchService.<RemainingAnnouncementEntity>searchSpecification(search)), pageable
                ).map(AnnouncementListMapper::toListItem);
                case PUBLIC_RENTAL -> publicRentalRepository.findAll(
                        visible(AnnouncementType.PUBLIC_RENTAL, AnnouncementSearchService.<PublicRentalAnnouncementEntity>searchSpecification(search)), pageable
                ).map(AnnouncementListMapper::toListItem);
                case OPTIONAL -> optionalRepository.findAll(
                        visible(AnnouncementType.OPTIONAL, AnnouncementSearchService.<OptionalAnnouncementEntity>searchSpecification(search)), pageable
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

        // 후보가 모자라면 빈 페이지가 되도록 범위를 목록 크기 안으로 맞춘다.
        int fromIndex = (int) Math.min((long) search.page() * search.size(), candidates.size());
        int toIndex = Math.min(fromIndex + search.size(), candidates.size());
        Pageable resultPage = PageRequest.of(search.page(), search.size());
        List<AnnouncementListItem> content = List.copyOf(candidates.subList(fromIndex, toIndex));
        return new PageImpl<>(commentCounts.enrich(content), resultPage, totalElements);
    }

    /** (단지명 또는 주소에 term) 그리고 scope가 있으면 (단지명 또는 주소에 scope 중 하나). LivingAreas.match와 같은 규칙이다. */
    // 대소문자 구분 없이 부분 일치(like %값%)로 찾는다. 검색어의 %, _, \ 는 글자 그대로 찾도록 이스케이프한다.
    private static Predicate livingAreaPredicate(CriteriaBuilder builder,
            Root<?> root, String addressProperty, LivingAreas.Matcher matcher) {
        Function<String, Predicate> contains = value -> {
            String pattern = "%" + value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return builder.or(builder.like(builder.lower(root.get("houseNm")), pattern, '\\'), builder.like(builder.lower(root.get(addressProperty)), pattern, '\\'));
        };
        var term = contains.apply(matcher.term());
        if (matcher.scope().isEmpty()) return term;
        return builder.and(term, builder.or(matcher.scope().stream().map(contains).toArray(Predicate[]::new)));
    }

    /**
     * 목록 검색 조건을 Specification으로 만든다. 값이 있는 조건만 and로 붙인다.
     * 제네릭 T라 5개 공고 엔티티에 함께 쓴다. 필드 이름이 다른 곳(주소·접수일)은 root.getJavaType()으로 갈라 처리한다.
     */
    private static <T> Specification<T> searchSpecification(AnnouncementSearch search) {
        return (root, query, builder) -> {
            // conjunction(): 항상 참인 조건. 여기서 출발해 조건을 하나씩 and로 붙인다.
            var predicates = builder.conjunction();
            if (search.regionCode() != null && !search.regionCode().isBlank()) {
                predicates = builder.and(predicates, builder.equal(root.get("subscrptAreaCode"), search.regionCode()));
            }
            if (search.keyword() != null && !search.keyword().isBlank()) {
                var matches = new ArrayList<Predicate>();
                // 도시형·오피스텔은 주소가 hssplyHshldco 필드(HSSPLY_HSHLDCO 컬럼)에 있다.
                String address = root.getJavaType() == UrbanAnnouncementEntity.class ? "hssplyHshldco" : "hssplyAdres";
                for (var matcher : LivingAreas.matchers(search.keyword())) {
                    matches.add(livingAreaPredicate(builder, root, address, matcher));
                }
                predicates = builder.and(predicates, builder.or(matches.toArray(Predicate[]::new)));
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
            // 정렬은 후보를 잘라 읽기 전에 정해야 하고, 페이지 경계가 흔들리지 않게 번호로 끝까지 정렬한다.
            // 같은 Specification이 count 쿼리(결과 타입 Long)에도 쓰이는데, count에는 정렬을 붙이지 않는다.
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

    // 접수 시작일에서 '-'를 빼 "20261001" 꼴로 맞춘 SQL 식(replace 함수). 문자열 그대로 크기 비교·정렬할 수 있다.
    private static <T> Expression<String> normalizedReceptionStartDate(
            Root<T> root,
            CriteriaBuilder builder
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

    // 어노테이션으로는 못 거르는 필드 간 검사.
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
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(AnnouncementType::fromPath)
                .forEach(selected::add);
        if (selected.isEmpty()) {
            return EnumSet.allOf(AnnouncementType.class);
        }
        return selected;
    }

    // 자바 쪽(메모리 정렬)에서 쓰는 같은 규칙. 숫자 8자리(yyyyMMdd)가 안 되면 null로 보고 맨 뒤로 보낸다.
    private static String normalizedDate(String date) {
        if (date == null) {
            return null;
        }
        // 유형별 접수일 형식을 통일해 날짜순으로 비교합니다.
        String digits = date.replaceAll("\\D", "");
        return digits.length() < 8 ? null : digits.substring(0, 8);
    }

    // 유형별로 앞에서 count개를 읽는 첫 페이지.
    private static Pageable candidatePage(long count) {
        return PageRequest.of(0, Math.toIntExact(count));
    }
}
