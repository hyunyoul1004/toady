/**
 * @file announcementApi.js
 * @description 청약 공고(목록, 상세, 댓글) API 통신 모듈
 * - 새 통합 분양공고 API (/api/v1/announcements)
 * - 유형별 공고 상세 API
 * - 공고 댓글 CRUD API (GET, POST, PATCH, DELETE)
 */
import apiClient from '@/api/client';
import { modelPrices } from '@/features/planner/researchUtils';
import { formatDate, formatDateRange } from '@/utils/date.util';

export const TYPE_TO_RCEPT_SE = {
  apt: '01',
  urban: '05',
  remaining: '06',
  'public-rental': '04',
  optional: '07'
};

/** 취소후재공급 상세는 remaining이며 경쟁률은 별도 리소스를 사용한다. */
export function getCompetitionType(detail = {}) {
  return detail.type === 'remaining' && String(detail.houseSecd) === '06'
    ? 'cancel-resupply' : (detail.type || 'apt');
}

function getDisplayRceptSe(item, type) {
  if (type === 'remaining') {
    if (item.houseSecd === '04') return '06';
    if (item.houseSecd === '06') return '08';
  }
  return TYPE_TO_RCEPT_SE[type] || '01';
}

/**
 * 청약홈 모집공고 상세 페이지 URL을 유형별로 조합합니다.
 * 백엔드에서 pblancUrl을 제공하지 않았을 때의 대체(fallback) URL로 사용됩니다.
 */
export function getFallbackPblancUrl(type, houseManageNo, pblancNo) {
  if (!houseManageNo || !pblancNo) return '#';
  const hNo = encodeURIComponent(houseManageNo);
  const pNo = encodeURIComponent(pblancNo);

  // APT (민영, 국민)
  if (type === 'apt') {
    return `https://www.applyhome.co.kr/ai/aia/selectAPTLttotPblancDetail.do?houseManageNo=${hNo}&pblancNo=${pNo}`;
  }
  // 무순위 / 잔여세대 / 취소후재공급
  if (type === 'remaining') {
    return `https://www.applyhome.co.kr/ai/aia/selectAPTRemndrLttotPblancDetailView.do?houseManageNo=${hNo}&pblancNo=${pNo}`;
  }
  // 오피스텔 / 도시형생활주택 / 민간임대 / 공공지원민간임대 / 임의공급 등
  return `https://www.applyhome.co.kr/ai/aia/selectPRMOLttotPblancDetailView.do?houseManageNo=${hNo}&pblancNo=${pNo}`;
}

/**
 * 식별자 문자열(예: 'apt/2026940234/2026940234') 또는 객체에서 type, houseManageNo, pblancNo를 파싱합니다.
 */
export function parseSubscriptId(idInput) {
  let type = 'apt';
  let houseManageNo = '';
  let pblancNo = '';

  if (!idInput) return { type, houseManageNo, pblancNo };

  if (typeof idInput === 'object') {
    type = idInput.type || type;
    houseManageNo = idInput.houseManageNo || '';
    pblancNo = idInput.pblancNo || houseManageNo;
    if (idInput.subscriptSeq && (!houseManageNo || !pblancNo)) {
      return parseSubscriptId(idInput.subscriptSeq);
    }
    return { type, houseManageNo, pblancNo };
  }

  const str = String(idInput);
  if (str.includes('/')) {
    const parts = str.split('/');
    if (parts.length >= 3) {
      type = parts[0];
      houseManageNo = parts[1];
      pblancNo = parts[2];
    } else if (parts.length === 2) {
      houseManageNo = parts[0];
      pblancNo = parts[1];
    } else {
      houseManageNo = parts[0];
      pblancNo = parts[0];
    }
  } else if (str.includes('_')) {
    const parts = str.split('_');
    if (parts.length >= 3) {
      type = parts[0];
      houseManageNo = parts[1];
      pblancNo = parts[2];
    } else if (parts.length === 2) {
      houseManageNo = parts[0];
      pblancNo = parts[1];
    } else {
      houseManageNo = parts[0];
      pblancNo = parts[0];
    }
  } else {
    houseManageNo = str;
    pblancNo = str;
  }

  return { type, houseManageNo, pblancNo };
}

/** 유효한 날짜면 'YYYY-MM-DD', 아니면 null */
function toDate(value) {
  const d = formatDate(value);
  return d && d !== '-' ? d : null;
}

/**
 * 순위(1/2순위) 컬럼들을 일정으로 변환.
 * 해당지역/경기/기타지역 접수일이 같으면 1건, 다르면 날짜별로 지역명을 붙여 분리.
 */
function buildRankSchedules(rceptSe, columns) {
  const byDate = new Map();
  columns.forEach(({ label, value }) => {
    const date = toDate(value);
    if (!date) return;
    if (!byDate.has(date)) byDate.set(date, []);
    byDate.get(date).push(label);
  });

  const multi = byDate.size > 1;
  return Array.from(byDate.entries()).map(([date, labels]) => ({
    rceptSe,
    inDate: date,
    areaLabel: multi ? labels.join('/') : ''
  }));
}

/**
 * APT 공고 1건(row) → 특별공급(01) / 1순위(02) / 2순위(03) 일정 목록.
 * 특별공급 전용 컬럼이 없으므로 청약접수 시작일(rceptBgnde)이 1순위 접수일보다 앞서면 특별공급일로 간주.
 */
export function buildAptSchedules(item) {
  const rank1 = buildRankSchedules('02', [
    { label: '해당지역', value: item.gnrlRnk1CrspareaRcptde },
    { label: '경기', value: item.gnrlRnk1EtcGgRcptde },
    { label: '기타지역', value: item.gnrlRnk1EtcAreaRcptde }
  ]);
  const rank2 = buildRankSchedules('03', [
    { label: '해당지역', value: item.gnrlRnk2CrspareaRcptde },
    { label: '경기', value: item.gnrlRnk2EtcGgRcptde },
    { label: '기타지역', value: item.gnrlRnk2EtcAreaRcptde }
  ]);

  const schedules = [];
  const rceptBgnde = toDate(item.rceptBgnde);
  const firstRank1 = rank1.map((s) => s.inDate).sort()[0];
  if (rceptBgnde && (!firstRank1 || rceptBgnde < firstRank1)) {
    schedules.push({ rceptSe: '01', inDate: rceptBgnde, areaLabel: '' });
  }
  schedules.push(...rank1, ...rank2);

  // 순위 정보가 하나도 없으면 접수시작일로 1건 표시
  if (schedules.length === 0 && rceptBgnde) {
    schedules.push({ rceptSe: '02', inDate: rceptBgnde, areaLabel: '' });
  }
  return schedules;
}

export const announcementApi = {
  /**
   * 분양 공고 통합 목록 조회
   * GET /api/v1/announcements
   */
  async getAnnouncements(params = {}) {
    const queryParams = {};

    // 1. 날짜 조건 매핑
    let startDate = params.startDate;
    let endDate = params.endDate;
    if (!startDate && params.startStr) startDate = formatDate(params.startStr);
    if (!endDate && params.endStr) endDate = formatDate(params.endStr);

    if (startDate && startDate !== '-') queryParams.startDate = startDate;
    if (endDate && endDate !== '-') queryParams.endDate = endDate;

    // 2. 주택 유형(types) 매핑
    if (params.types) {
      queryParams.types = params.types;
    } else {
      const selected = [];
      const isApt = params.rceptSe01 === 'Y' || params.rceptSe02 === 'Y' || params.rceptSe03 === 'Y';
      const isUrban = params.rceptSe05 === 'Y';
      const isRemaining = params.rceptSe06 === 'Y' || params.rceptSe08 === 'Y'; // 무순위 또는 불법행위재공급 선택 시 remaining 요청
      const isPublicRental = params.rceptSe04 === 'Y';
      const isOptional = params.rceptSe07 === 'Y'; // 임의공급 선택 시 optional 요청

      const allChecked = isApt && isUrban && isRemaining && isPublicRental && isOptional;
      const allUnchecked = !isApt && !isUrban && !isRemaining && !isPublicRental && !isOptional;

      if (!allChecked && !allUnchecked) {
        if (isApt) selected.push('apt');
        if (isUrban) selected.push('urban');
        if (isRemaining) selected.push('remaining');
        if (isPublicRental) selected.push('public-rental');
        if (isOptional) selected.push('optional');
        if (selected.length > 0) queryParams.types = selected.join(',');
      }
    }

    // 3. 지역 및 검색어 매핑
    if (params.regionCode) queryParams.regionCode = params.regionCode;
    if (params.keyword) {
      queryParams.keyword = params.keyword;
    }
    // 주의: params.citys는 프론트엔드에서 실시간으로 정밀 필터링하므로,
    // 서버 queryParams.keyword에 임의로 전달하지 않습니다.
    // (서버 검색 컬럼이 houseNm 등으로 한정되어 있으면 hssplyAdres 검색 시 데이터가 통째로 누락되는 문제 방지)

    // 4. 페이징 (달력 및 리스트 조회를 위해 100건 기본)
    queryParams.page = params.page != null ? params.page : 0;
    queryParams.size = params.size != null ? params.size : 100;

    const res = await apiClient.get('/api/v1/announcements', queryParams);
    const content = [...(res.data?.content || [])];
    if (params.page == null) {
      // Calendar data must include every page in the selected date range.
      const totalPages = res.data?.totalPages || 1;
      for (let page = 1; page < totalPages; page += 1) {
        const next = await apiClient.get('/api/v1/announcements', { ...queryParams, page });
        content.push(...(next.data?.content || []));
      }
    }

    // UI 컴포넌트 호환 매핑
    // - APT: 한 공고(row)의 특별공급/1순위/2순위 컬럼을 각각의 일정으로 펼침
    // - 그 외 유형: 청약접수 시작일(rceptBgnde) 기준 1건
    const resultList = content.flatMap((item) => {
      const type = item.type || 'apt';
      const houseManageNo = item.houseManageNo;
      const pblancNo = item.pblancNo || item.houseManageNo;
      const subscriptSeq = `${type}/${houseManageNo}/${pblancNo}`;
      const displayRceptSe = getDisplayRceptSe(item, type);

      const base = {
        ...item,
        type,
        displayRceptSe,
        houseManageNo,
        pblancNo,
        subscriptSeq,
        supplyLocation: item.hssplyAdres || item.regionName || (item.regionCode ? `지역 ${item.regionCode}` : '기타'),
        hssplyAdres: item.hssplyAdres || '',
        subscriptCommentCnt: item.commentCount || 0
      };

      if (type !== 'apt') {
        const effectiveRceptSe = displayRceptSe || TYPE_TO_RCEPT_SE[type] || '01';
        return [{
          ...base,
          rceptSe: effectiveRceptSe,
          displayRceptSe: effectiveRceptSe,
          inDate: formatDate(item.rceptBgnde)
        }];
      }

      return buildAptSchedules(item).map((s) => ({
        ...base,
        ...s,
        displayRceptSe: s.rceptSe
      }));
    });

    return {
      ...res,
      data: {
        ...res.data,
        resultList,
        pagination: {
          page: res.data?.page || 0,
          size: res.data?.size || resultList.length,
          totalElements: res.data?.totalElements || resultList.length,
          totalPages: res.data?.totalPages || 1
        }
      }
    };
  },

  /**
   * 공고 상세 조회
   * GET /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}
   */
  async getAnnouncementDetail(params = {}) {
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';

    const detailPromise = apiClient.get(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}`);
    const detailRes = await detailPromise;
    const anno = detailRes.data?.announcement || {};
    const models = detailRes.data?.models || [];
    const commentCount = detailRes.data?.commentCount || 0;
    const rceptSe = TYPE_TO_RCEPT_SE[type] || '01';

    const subscriptDetail = {
      ...anno,
      subscriptSeq: `${type}/${anno.houseManageNo || houseManageNo}/${anno.pblancNo || pblancNo}`,
      type,
      houseManageNo: anno.houseManageNo || houseManageNo,
      pblancNo: anno.pblancNo || pblancNo,
      pblancUrl: anno.pblancUrl || '',
      houseNm: anno.houseNm || '-',
      rceptSe,
      hssplyAdres: anno.hssplyAdres || anno.hssplyHshldco || anno.hssply_adres || anno.supplyLocation || '',
      supplyLocation: anno.hssplyAdres || anno.hssplyHshldco || anno.hssply_adres || anno.subscrptAreaCodeNm || '-',
      unitPrices: modelPrices(models,type),
      priceSummary: type === 'apt' ? models.filter(m => m.lttotTopAmount).slice(0,5).map(m => `${m.houseTy || m.modelNo}: ${m.lttotTopAmount}만원 (최고가)`).join(' / ') : '',
      supplyScale: anno.totSuplyHshldco ? `${anno.totSuplyHshldco} 세대` : '-',
      developer: anno.bsnsMbyNm || anno.developer || '-',
      contractor: anno.cnstrctEntrpsNm || anno.contractor || '-',
      businessPhoneNumber: anno.mdhsTelno || anno.businessPhoneNumber || '-',
      upcomingMonth: formatDate(anno.mvnPrearngeYm),
      recruitmentNoticeFileUrl: (anno.pblancUrl && String(anno.pblancUrl).trim() !== '' && anno.pblancUrl !== '#')
        ? String(anno.pblancUrl).trim()
        : getFallbackPblancUrl(type, anno.houseManageNo || houseManageNo, anno.pblancNo || pblancNo),
      applicationDate: formatDate(anno.rcritPblancDe),
      subscriptDate: formatDateRange(anno.rceptBgnde || anno.subscrptRceptBgnde, anno.rceptEndde || anno.subscrptRceptEndde),
      specialPriorityArea: formatDateRange(anno.spsplyRceptBgnde, anno.spsplyRceptEndde),
      specialOtherAreas: formatDateRange(anno.spsplyRceptBgnde, anno.spsplyRceptEndde),
      specialPlaceOfReception: '청약홈 (인터넷)',
      onePriorityArea: formatDateRange(anno.gnrlRnk1CrspareaRcptde, anno.gnrlRnk1CrspareaEndde),
      oneOtherAreas: formatDateRange(anno.gnrlRnk1EtcAreaRcptde, anno.gnrlRnk1EtcAreaEndde),
      onePlaceOfReception: '청약홈 (인터넷)',
      twoPriorityArea: formatDateRange(anno.gnrlRnk2CrspareaRcptde, anno.gnrlRnk2CrspareaEndde),
      twoOtherAreas: formatDateRange(anno.gnrlRnk2EtcAreaRcptde, anno.gnrlRnk2EtcAreaEndde),
      twoPlaceOfReception: '청약홈 (인터넷)',
      winnerAnnouncementDate: formatDate(anno.przwnerPresnatnDe),
      contractDate: formatDateRange(anno.cntrctCnclsBgnde, anno.cntrctCnclsEndde),
      housingDivision: anno.houseSecdNm || anno.houseSecd || '-',
      subscriptCommentCnt: commentCount
    };

    const subscriptSupply = models.map((m, idx) => {
      const normalCnt = Number(m.suplyHshldco || m.gnsplyHshldco) || 0;
      const specialCnt = Number(m.spsplyHshldco) || 0;
      return {
        subscriptSupplySeq: m.id ?? m.modelNo ?? (idx + 1),
        housingType: m.houseTy || m.tp || '-',
        housingSupplyArea: m.suplyAr || m.supplyAr || m.excluseAr || m.houseTy || '-',
        supplyNormalCnt: normalCnt,
        supplySpecialCnt: specialCnt,
        supplyTotalCnt: normalCnt + specialCnt > 0 ? normalCnt + specialCnt : normalCnt,
        housingModelNo: m.modelNo || (idx + 1),
        note: m.note || ''
      };
    });

    const subscriptNumberTwo = models.map((m, idx) => ({
      subscriptNumberTwoSeq: m.id ?? m.modelNo ?? (idx + 1),
      twoHousingType: m.houseTy || m.tp || '-',
      twoSupplyAmount: m.lttotTopAmount || m.suplyAmount || '-'
    }));

    const subscriptSpecial = models.map((m, idx) => ({
      subscriptSpecialSeq: m.modelNo || idx + 1,
      specialHousingType: m.houseTy || m.tp || '-',
      specialMultiFamily: m.mnychHshldco != null ? Number(m.mnychHshldco) : 0,
      specialHoneymoon: m.nwwdsHshldco != null ? Number(m.nwwdsHshldco) : (m.spsplyNewMrrgHshldco != null ? Number(m.spsplyNewMrrgHshldco) : 0),
      specialFirstTimeInLife: m.lfeFrstHshldco != null ? Number(m.lfeFrstHshldco) : 0,
      specialOldParents: m.oldParntsSuportHshldco != null ? Number(m.oldParntsSuportHshldco) : (m.spsplyAgedHshldco != null ? Number(m.spsplyAgedHshldco) : 0),
      specialRecommendation: m.insttRecomendHshldco != null ? Number(m.insttRecomendHshldco) : (m.spsplyYgmnHshldco != null ? Number(m.spsplyYgmnHshldco) : 0),
      specialFormerInstitution: m.transrInsttEnfsnHshldco != null ? Number(m.transrInsttEnfsnHshldco) : 0,
      specialOther: m.etcHshldco != null ? Number(m.etcHshldco) : 0,
      specialTotal: m.spsplyHshldco != null ? Number(m.spsplyHshldco) : 0
    }));

    return {
      resultCode: 'SUCCESS',
      resultMessage: '성공',
      data: {
        subscriptDetail,
        subscriptSupply,
        subscriptNumberTwo,
        subscriptSpecial
      }
    };
  },

  /**
   * 공고 댓글 목록 조회
   * GET /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}/comments
   */
  async getComments(params = {}) {
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';

    const page = params.page != null ? params.page : 0;
    const size = params.size != null ? params.size : 100;

    const res = await apiClient.get(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}/comments`, { page, size });
    const content = [...(res.data?.content || (Array.isArray(res.data) ? res.data : []))];
    // 번호 커서로 이전 댓글을 조회해 신규 등록·삭제가 페이지 경계를 이동시키지 않게 한다.
    if (params.page == null) {
      let current = res.data;
      let previousCursor = Infinity;
      while ((current?.totalPages || 1) > 1) {
        const beforeCommentSeq = current.content?.at(-1)?.commentSeq;
        if (!Number.isSafeInteger(beforeCommentSeq) || beforeCommentSeq <= 0 || beforeCommentSeq >= previousCursor) {
          throw new Error('댓글 페이지 정보를 확인할 수 없습니다. 다시 조회해 주세요.');
        }
        previousCursor = beforeCommentSeq;
        const next = await apiClient.get(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}/comments`, { page: 0, size, beforeCommentSeq });
        current = next.data;
        content.push(...(current?.content || []));
      }
    }

    const subscriptCommentList = content.map((c) => ({
      ...c,
      subscriptCommentSeq: c.commentSeq,
      commentSeq: c.commentSeq,
      subscriptSeq: `${c.announcementType || type}/${c.houseManageNo || houseManageNo}/${c.pblancNo || pblancNo}`,
      houseManageNo: c.houseManageNo || houseManageNo,
      pblancNo: c.pblancNo || pblancNo,
      parentSubscriptCommentSeq: c.parentCommentSeq,
      parentCommentSeq: c.parentCommentSeq,
      createDt: c.regDt,
      regDt: c.regDt
    }));

    return {
      ...res,
      data: {
        ...res.data,
        subscriptCommentList
      }
    };
  },

  /**
   * 댓글 등록
   * POST /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}/comments
   */
  async addComment(params = {}) {
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';

    const payload = {
      parentCommentSeq: params.parentCommentSeq || params.parentSubscriptCommentSeq || null,
      content: params.content
    };

    const res = await apiClient.post(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}/comments`, payload);
    return {
      ...res,
      resultCode: res.resultCode || 'SUCCESS',
      resultMessage: res.resultMessage || '댓글이 성공적으로 등록되었습니다.'
    };
  },

  /**
   * 댓글 수정
   * PATCH /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}/comments/{commentSeq}
   */
  async updateComment(params = {}) {
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';
    const commentSeq = params.commentSeq || params.subscriptCommentSeq;

    const payload = { content: params.content };
    const res = await apiClient.patch(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}/comments/${commentSeq}`, payload);
    return {
      ...res,
      resultCode: res.resultCode || 'SUCCESS',
      resultMessage: res.resultMessage || '댓글이 수정되었습니다.'
    };
  },

  /**
   * 댓글 삭제
   * DELETE /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}/comments/{commentSeq}
   */
  async deleteComment(params = {}) {
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';
    const commentSeq = params.commentSeq || params.subscriptCommentSeq;

    const res = await apiClient.delete(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}/comments/${commentSeq}`);
    return {
      ...res,
      resultCode: res?.resultCode || 'SUCCESS',
      resultMessage: res?.resultMessage || '댓글이 삭제되었습니다.'
    };
  },

  /**
   * 청약 경쟁률 조회
   * GET /api/v1/{type}/announcements/{houseManageNo}/{pblancNo}/competition-rates
   */
  async getCompetitionRates(params = {}) {
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';

    const res = await apiClient.get(`/api/v1/${type}/announcements/${houseManageNo}/${pblancNo}/competition-rates`);
    const list = res.data?.data || (Array.isArray(res.data) ? res.data : []);
    return {
      resultCode: res.data?.resultCode || 'SUCCESS',
      resultMessage: res.data?.resultMessage || '성공',
      data: list
    };
  },

  /**
   * APT 당첨 가점 조회
   * GET /api/v1/apt/announcements/{houseManageNo}/{pblancNo}/scores
   */
  async getAptScores(params = {}) {
    let { houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;

    const res = await apiClient.get(`/api/v1/apt/announcements/${houseManageNo}/${pblancNo}/scores`);
    const list = res.data?.data || (Array.isArray(res.data) ? res.data : []);
    return {
      resultCode: res.data?.resultCode || 'SUCCESS',
      resultMessage: res.data?.resultMessage || '성공',
      data: list
    };
  },

  /**
   * 전체 기간 공고 자동완성 빠른 검색
   * GET /api/v1/announcements/suggestions
   * @param {{ keyword: string, limit?: number }} params
   */
  async getAnnouncementSuggestions(params = {}) {
    if (!params.keyword || !params.keyword.trim()) {
      return { resultCode: 'SUCCESS', resultMessage: '성공', data: [] };
    }
    const res = await apiClient.get('/api/v1/announcements/suggestions', {
      keyword: params.keyword.trim(),
      limit: params.limit || 10
    });
    const list = res.data?.data || (Array.isArray(res.data) ? res.data : []);
    return {
      resultCode: res.data?.resultCode || 'SUCCESS',
      resultMessage: res.data?.resultMessage || '성공',
      data: list
    };
  },

  /**
   * APT 특별공급 신청현황 조회
   * GET /api/v1/apt/announcements/{houseManageNo}/{pblancNo}/special-supply-status
   */
  async getAptSpecialSupplyStatus(params = {}) {
    let { houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;

    const res = await apiClient.get(`/api/v1/apt/announcements/${houseManageNo}/${pblancNo}/special-supply-status`);
    const list = res.data?.data || (Array.isArray(res.data) ? res.data : []);
    return {
      resultCode: res.data?.resultCode || 'SUCCESS',
      resultMessage: res.data?.resultMessage || '성공',
      data: list
    };
  }
};

export default announcementApi;
