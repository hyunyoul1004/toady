/**
 * @file announcementApi.js
 * @description 청약 공고(목록, 상세, 댓글) API 통신 모듈
 * - 새 통합 분양공고 API (/api/v1/announcements)
 * - 유형별 공고 상세 API
 * - 공고 댓글 CRUD API (GET, POST, PATCH, DELETE)
 * - 경쟁률·당첨가점·특별공급 신청현황, 자동완성 검색
 * 서버 응답을 예전 화면 컴포넌트가 쓰던 필드 이름(subscriptDetail, resultList 등)으로 바꿔 돌려준다.
 * 화면에서는 보통 hooks/useAnnouncements.js의 React Query 훅을 거쳐 쓰고,
 * views/main/hooks/useAnnouncementSearch.js는 자동완성 검색을 직접 부른다.
 */
import apiClient from '@/api/client';
import { modelPrices } from '@/features/planner/researchUtils';
import { formatDate, formatDateRange } from '@/utils/date.util';
import { buildAnnouncementQueryParams } from '@/features/announcement/api/announcementParams';
import { scheduleRows } from '@/features/announcement/applyDays';

/** 서버 통합 검색(AnnouncementSearchService.searchAll)이 한 번에 허용하는 범위: (page+1)×size ≤ 1000. */
const MAX_LIST_ITEMS = 1000;

/** 공고 유형 → 화면의 접수 구분 코드(rceptSe). 달력 색·필터가 이 코드를 쓴다. */
const TYPE_TO_RCEPT_SE = {
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

/** 목록에 보일 접수 구분 코드. remaining은 houseSecd로 무순위(06)·불법행위재공급(08)을 나눈다. */
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
function getFallbackPblancUrl(type, houseManageNo, pblancNo) {
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
    return safeParts({ type, houseManageNo, pblancNo });
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

  return safeParts({ type, houseManageNo, pblancNo });
}

const TYPE_PART = /^(apt|urban|remaining|public-rental|optional|cancel-resupply)$/;
const KEY_PART = /^[a-zA-Z0-9_-]{1,40}$/;
/**
 * 공고 API 경로. 형식이 틀린 값은 요청하지 않고 오류로 끝낸다(다른 API 경로로 새지 않게).
 * 공유 링크(?subscriptSeq=)로 들어온 값이 API 경로에 그대로 들어가므로 형식을 확인한다. '..'·'?'·'#' 등이 섞이면
 * 로그인 토큰을 실은 요청이 다른 API 경로로 갈 수 있기 때문이다.
 */
function announcementPath(type, houseManageNo, pblancNo) {
  if (!TYPE_PART.test(type || '') || !KEY_PART.test(houseManageNo || '') || !KEY_PART.test(pblancNo || '')) throw new Error('공고 주소가 올바르지 않습니다.');
  // 형식 검사를 통과한 값만 쓰지만, 경로 조각은 항상 인코딩해 둔다(검사 규칙이 바뀌어도 경로가 깨지지 않게).
  return `/api/v1/${encodeURIComponent(type)}/announcements/${encodeURIComponent(houseManageNo)}/${encodeURIComponent(pblancNo)}`;
}
/** 댓글 하나의 API 경로. 댓글 번호는 양의 정수만 받는다(빈 값·'1/../x' 같은 값으로 다른 경로를 부르지 않게). */
function commentPath(type, houseManageNo, pblancNo, commentSeq) {
  const seq = String(commentSeq ?? '');
  if (!/^[1-9][0-9]{0,18}$/.test(seq)) throw new Error('댓글 번호가 올바르지 않습니다.');
  return `${announcementPath(type, houseManageNo, pblancNo)}/comments/${encodeURIComponent(seq)}`;
}
/** parseSubscriptId의 마지막 확인: 형식이 틀리면 빈 값으로 돌려 상세를 열지 않는다. */
function safeParts(parts) {
  if (!TYPE_PART.test(parts.type) || !KEY_PART.test(parts.houseManageNo) || !KEY_PART.test(parts.pblancNo)) return { type: 'apt', houseManageNo: '', pblancNo: '' };
  return parts;
}

/** 공고 API 모음. 각 함수는 apiClient(fetch 기반 공통 클라이언트, 로그인 토큰 자동 첨부)로 요청하고 화면용 모양으로 바꿔 돌려준다. */
export const announcementApi = {
  /**
   * 분양 공고 통합 목록 조회
   * GET /api/v1/announcements
   */
  async getAnnouncements(params = {}) {
    const queryParams = buildAnnouncementQueryParams(params, formatDate);

    const res = await apiClient.get('/api/v1/announcements', queryParams);
    const content = [...(res.data?.content || [])];
    let truncated = false;
    if (params.page == null) {
      // 달력은 고른 기간의 공고가 모두 있어야 하므로, page를 지정하지 않으면 나머지 페이지도 차례로 받아 합친다.
      // 서버 통합 검색은 (page+1)×size가 MAX_LIST_ITEMS를 넘으면 400으로 거절하므로 그 앞 페이지까지만 받는다.
      // 넘는 부분(접수 시작일이 오래된 공고)은 빠지지만 달력 전체가 오류가 되지는 않게 하고, truncated로 알린다.
      const size = Number(queryParams.size) || 100;
      const totalPages = res.data?.totalPages || 1;
      const lastPage = Math.min(totalPages, Math.floor(MAX_LIST_ITEMS / size));
      truncated = totalPages > lastPage;
      if (truncated) console.warn(`공고가 ${MAX_LIST_ITEMS}건을 넘어 접수 시작일이 오래된 일부 공고는 달력에 빠질 수 있습니다.`);
      for (let page = 1; page < lastPage; page += 1) {
        const next = await apiClient.get('/api/v1/announcements', { ...queryParams, page });
        content.push(...(next.data?.content || []));
      }
    }

    // UI 컴포넌트 호환 매핑: 공고 한 건(row)을 신청 가능일마다 한 행으로 펼친다(규칙: features/announcement/applyDays.js).
    // - APT: 특별공급/1순위/2순위 접수일. 순위 날짜가 없으면 접수 기간 전체.
    // - 그 외 유형: 접수 시작일~종료일의 모든 날. 이튿날부터는 ongoing(달력의 연한 '접수 중' 줄).
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

      return scheduleRows({ ...item, type }, displayRceptSe || TYPE_TO_RCEPT_SE[type] || '01')
        .map((row) => ({ ...base, ...row, displayRceptSe: row.rceptSe }));
    });

    return {
      ...res,
      data: {
        ...res.data,
        resultList,
        truncated,
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
    // subscriptSeq 문자열이나 { type, houseManageNo, pblancNo } 객체 모두 받는다(아래 다른 함수도 같은 방식).
    let { type, houseManageNo, pblancNo } = parseSubscriptId(params.subscriptSeq || params);
    if (params.type) type = params.type;
    if (params.houseManageNo) houseManageNo = params.houseManageNo;
    if (params.pblancNo) pblancNo = params.pblancNo;
    type = type || 'apt';

    const detailPromise = apiClient.get(`${announcementPath(type, houseManageNo, pblancNo)}`);
    const detailRes = await detailPromise;
    const anno = detailRes.data?.announcement || {};
    const models = detailRes.data?.models || [];
    const commentCount = detailRes.data?.commentCount || 0;
    const rceptSe = TYPE_TO_RCEPT_SE[type] || '01';

    // 상세 화면이 쓰는 필드 모양으로 맞춘다. 값이 없을 때 대신 쓸 필드 이름을 || 로 이어 둔 것이 많다.
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
      subscriptCommentCnt: commentCount,
      // 운영자 안내(정정·취소 공고)와 숨김 여부. 관리자 사이트에서 정한다.
      operatorNotice: detailRes.data?.operatorNotice || null
    };

    // 아래 세 목록은 주택형(models)을 공급 세대수·공급금액·특별공급 표 모양으로 바꾼 것이다.
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

    const res = await apiClient.get(`${announcementPath(type, houseManageNo, pblancNo)}/comments`, { page, size });
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
        const next = await apiClient.get(`${announcementPath(type, houseManageNo, pblancNo)}/comments`, { page: 0, size, beforeCommentSeq });
        current = next.data;
        content.push(...(current?.content || []));
      }
    }

    const subscriptCommentList = content.map((c) => ({
      ...c,
      // 운영팀 글과 맨 위 고정 여부(관리자 사이트에서 정함).
      official: Boolean(c.official),
      pinned: Boolean(c.pinned),
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

    const res = await apiClient.post(`${announcementPath(type, houseManageNo, pblancNo)}/comments`, payload);
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
    const res = await apiClient.patch(commentPath(type, houseManageNo, pblancNo, commentSeq), payload);
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

    const res = await apiClient.delete(commentPath(type, houseManageNo, pblancNo, commentSeq));
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

    const res = await apiClient.get(`${announcementPath(type, houseManageNo, pblancNo)}/competition-rates`);
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

    const res = await apiClient.get(`${announcementPath('apt', houseManageNo, pblancNo)}/scores`);
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

    const res = await apiClient.get(`${announcementPath('apt', houseManageNo, pblancNo)}/special-supply-status`);
    const list = res.data?.data || (Array.isArray(res.data) ? res.data : []);
    return {
      resultCode: res.data?.resultCode || 'SUCCESS',
      resultMessage: res.data?.resultMessage || '성공',
      data: list
    };
  }
};

export default announcementApi;
