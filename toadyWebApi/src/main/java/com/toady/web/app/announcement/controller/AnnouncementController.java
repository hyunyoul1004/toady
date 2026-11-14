package com.toady.web.app.announcement.controller;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.app.announcement.CompetitionDataType;
import com.toady.web.app.announcement.dto.AnnouncementCommentRequest;
import com.toady.web.app.announcement.dto.AnnouncementCommentResponse;
import com.toady.web.app.announcement.dto.AnnouncementCommentUpdateRequest;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.dto.AnnouncementSuggestionResponse;
import com.toady.web.app.announcement.service.AnnouncementCommentService;
import com.toady.web.app.announcement.service.AnnouncementService;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 공고와 댓글의 목록·상세 조회 및 작성·수정·삭제 요청을 처리합니다. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "분양 공고 API", description = "분양 공고 유형별 목록·상세 및 댓글 조회/관리 API")
public class AnnouncementController {
    private final AnnouncementService announcementService;
    private final AnnouncementCommentService commentService;

    @GetMapping("/announcements")
    @Operation(
            summary = "전체 유형 분양 공고 통합 조회",
            description = """
                    아파트, 오피스텔·도시형·민간임대, 잔여세대, 공공지원 민간임대, 임의공급 공고를
                    하나의 목록으로 합쳐 청약접수 시작일 최신순으로 조회합니다.
                    types를 생략하면 5개 유형 전체를 조회하고, 특정 유형만 조회하려면 apt처럼 한 개를 전달합니다.
                    여러 유형은 types=apt,urban처럼 콤마로 구분하거나 types=apt&types=urban으로 전달할 수 있습니다.
                    regionCode, keyword, startDate, endDate는 선택 조건이며 날짜 조건은 청약접수 시작일을 기준으로 합니다.
                    목록의 rceptBgnde에는 해당 유형의 청약접수 시작일을 반환합니다.
                    조건을 생략하면 해당 조건 없이 조회합니다.
                    결과는 페이지 단위로 반환하며 page는 0부터 시작하고 size는 1~100입니다.
                    """
    )
    public ResponseEntity<ResData<?>> listAll(
            @Parameter(description = "조회할 공고 유형. 생략하면 전체 유형. 여러 유형은 콤마로 구분합니다.", example = "apt,urban")
            @RequestParam(required = false) List<String> types,
            @Valid @ParameterObject @ModelAttribute AnnouncementSearch search
    ) {
        return ResponseEntity.ok(ResData.of(pageData(
                announcementService.searchAll(search, types)
        ), ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

    @GetMapping("/announcements/suggestions")
    @Operation(
            summary = "전체 기간 공고 자동완성 검색",
            description = "전체 기간의 5개 공고 유형에서 주택명 또는 주소에 키워드가 포함된 결과를 청약접수 시작일 최신순으로 반환합니다."
    )
    public ResponseEntity<ResData<List<AnnouncementSuggestionResponse>>> suggestions(
            @Parameter(description = "검색 키워드 (공백이면 빈 목록 반환)", example = "자이")
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @Parameter(description = "최대 반환 건수 (1~20)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit
    ) {
        return ResponseEntity.ok(ResData.of(
                announcementService.suggestions(keyword, limit),
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage()
        ));
    }

    /** 검색 조건을 적용해 지정된 공고 유형의 페이지 목록을 반환합니다. */
    @GetMapping("/{type}/announcements")
    @Operation(hidden = true)
    public ResponseEntity<ResData<?>> list(
            @Parameter(description = "공고 유형: apt, urban, remaining, public-rental, optional", example = "apt")
            @PathVariable String type,
            @Valid @ParameterObject @ModelAttribute AnnouncementSearch search
    ) {
        return ResponseEntity.ok(ResData.of(pageData(
                announcementService.search(AnnouncementType.fromPath(type), search)
        ), ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

    @GetMapping("/{type}/announcements/{houseManageNo}/{pblancNo}")
    @Operation(
            summary = "분양 공고 상세 조회",
            description = "주택관리번호와 공고번호로 공고 상세정보를 조회합니다. 응답에는 해당 공고의 댓글 수(commentCount)가 포함됩니다."
    )
    public ResponseEntity<ResData<?>> detail(
            @Parameter(description = "공고 유형: apt, urban, remaining, public-rental, optional", example = "apt")
            @PathVariable String type,
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo
    ) {
        AnnouncementType announcementType = AnnouncementType.fromPath(type);
        Map<String, Object> data = announcementService.getDetail(
                announcementType, houseManageNo, pblancNo
        );
        data.put("commentCount", commentService.count(announcementType, houseManageNo, pblancNo));
        return ResponseEntity.ok(ResData.of(
                data,
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage()
        ));
    }

    @GetMapping("/{type}/announcements/{houseManageNo}/{pblancNo}/competition-rates")
    @Operation(
            summary = "공고 경쟁률 조회",
            description = """
                    해당 공고 유형의 경쟁률 데이터를 조회합니다.
                    지원 유형: apt, urban, public-rental, cancel-resupply, remaining, optional.
                    """
    )
    public ResponseEntity<ResData<?>> competitionRates(
            @Parameter(description = "경쟁률 유형: apt, urban, public-rental, cancel-resupply, remaining, optional")
            @PathVariable String type,
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo
    ) {
        return ResponseEntity.ok(ResData.of(
                announcementService.getCompetitionRates(
                        CompetitionDataType.fromPath(type), houseManageNo, pblancNo
                ),
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage()
        ));
    }

    @GetMapping("/apt/announcements/{houseManageNo}/{pblancNo}/scores")
    @Operation(
            summary = "APT 당첨가점 조회",
            description = "APT 공고의 주택형·거주지역별 최저·최고·평균 당첨가점을 조회합니다."
    )
    public ResponseEntity<ResData<?>> aptScores(
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo
    ) {
        return ResponseEntity.ok(ResData.of(
                announcementService.getAptScores(houseManageNo, pblancNo),
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage()
        ));
    }

    @GetMapping("/apt/announcements/{houseManageNo}/{pblancNo}/special-supply-status")
    @Operation(
            summary = "APT 특별공급 신청현황 조회",
            description = "APT 공고의 주택형별 특별공급 배정세대수와 접수현황을 조회합니다."
    )
    public ResponseEntity<ResData<?>> aptSpecialSupplyStatus(
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo
    ) {
        return ResponseEntity.ok(ResData.of(
                announcementService.getAptSpecialSupplyStatus(houseManageNo, pblancNo),
                ResultCode.SUCCESS.getCode(),
                ResultCode.SUCCESS.getMessage()
        ));
    }

    @GetMapping("/{type}/announcements/{houseManageNo}/{pblancNo}/comments")
    @Operation(
            summary = "공고 댓글 목록 조회",
            description = "공고에 작성된 댓글을 페이지 단위로 조회합니다. 기본 페이지는 0, 기본 페이지 크기는 20이며 페이지 크기는 최대 100입니다."
    )
    public ResponseEntity<ResData<?>> comments(
            @Parameter(description = "공고 유형: apt, urban, remaining, public-rental, optional", example = "apt")
            @PathVariable String type,
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo,
            @Parameter(description = "조회할 페이지(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @Parameter(description = "페이지당 댓글 수(1~100)", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "이 댓글 번호보다 이전 댓글 조회. 커서 사용 시 page=0")
            @RequestParam(required = false) Long beforeCommentSeq
    ) {
        Page<AnnouncementCommentResponse> result = commentService.list(
                AnnouncementType.fromPath(type), houseManageNo, pblancNo, page, size, beforeCommentSeq
        );
        return ResponseEntity.ok(ResData.of(pageData(result), ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

    @PostMapping("/{type}/announcements/{houseManageNo}/{pblancNo}/comments")
    @Secured("ROLE_USER")
    @Operation(
            summary = "공고 댓글 작성",
            description = "로그인한 사용자가 공고에 댓글 또는 답글을 작성합니다. 답글인 경우 parentCommentSeq에 부모 댓글 번호를 전달합니다."
    )
    public ResponseEntity<ResData<?>> createComment(
            @Parameter(description = "공고 유형: apt, urban, remaining, public-rental, optional", example = "apt")
            @PathVariable String type,
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "댓글 내용과 선택적 부모 댓글 번호",
                    required = true
            )
            @Valid @RequestBody AnnouncementCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        AnnouncementCommentResponse comment = commentService.create(
                AnnouncementType.fromPath(type), houseManageNo, pblancNo, request, user.getUserSeq()
        );
        return ResponseEntity.ok(ResData.of(comment, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

    @PatchMapping("/{type}/announcements/{houseManageNo}/{pblancNo}/comments/{commentSeq}")
    @Secured("ROLE_USER")
    @Operation(
            summary = "공고 댓글 수정",
            description = "로그인한 작성자가 본인의 댓글 내용을 수정합니다. 댓글 내용은 비어 있을 수 없으며 최대 4,000자입니다."
    )
    public ResponseEntity<ResData<?>> updateComment(
            @Parameter(description = "공고 유형: apt, urban, remaining, public-rental, optional", example = "apt")
            @PathVariable String type,
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo,
            @Parameter(description = "수정할 댓글 번호", example = "1")
            @PathVariable Long commentSeq,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "수정할 댓글 내용",
                    required = true
            )
            @Valid @RequestBody AnnouncementCommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        AnnouncementCommentResponse comment = commentService.update(
                AnnouncementType.fromPath(type),
                houseManageNo,
                pblancNo,
                commentSeq,
                request,
                user.getUserSeq()
        );
        return ResponseEntity.ok(ResData.of(comment, ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

    @DeleteMapping("/{type}/announcements/{houseManageNo}/{pblancNo}/comments/{commentSeq}")
    @Secured("ROLE_USER")
    @Operation(
            summary = "공고 댓글 삭제",
            description = "로그인한 작성자가 본인의 댓글을 삭제 처리합니다. 실제 데이터의 보존/삭제 방식은 서버 설정에 따릅니다."
    )
    public ResponseEntity<ResData<?>> deleteComment(
            @Parameter(description = "공고 유형: apt, urban, remaining, public-rental, optional", example = "apt")
            @PathVariable String type,
            @Parameter(description = "주택관리번호", example = "2025000123")
            @PathVariable String houseManageNo,
            @Parameter(description = "공고번호", example = "2025000123")
            @PathVariable String pblancNo,
            @Parameter(description = "삭제할 댓글 번호", example = "1")
            @PathVariable Long commentSeq,
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        commentService.delete(
                AnnouncementType.fromPath(type),
                houseManageNo,
                pblancNo,
                commentSeq,
                user.getUserSeq()
        );
        return ResponseEntity.ok(ResData.of(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage()));
    }

    private static Map<String, Object> pageData(Page<?> page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getContent());
        result.put("page", page.getNumber());
        result.put("size", page.getSize());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        return result;
    }
}
