/**
 * @file announcementParams.js
 * @description 공고 목록 화면 조건(paramInfo)을 서버 조회 파라미터로 바꾸는 순수 함수.
 * 목록 조회 요청과 TanStack Query 키가 같은 함수를 써서, 서버 요청이 같으면 캐시도 같게 한다.
 * (검색 지역 citys는 화면에서만 거르는 조건이라 서버 파라미터·쿼리 키에 넣지 않는다.)
 */

/**
 * @param {Record<string, any>} params 화면 조건 (startStr/endStr 또는 startDate/endDate, rceptSe01~08, types, regionCode, keyword, page, size)
 * @param {(value: any) => string} [toDate] 날짜 문자열을 YYYY-MM-DD로 맞추는 함수(값이 없으면 '-' 반환)
 * @returns {Record<string, any>} 서버에 보내는 쿼리 파라미터
 */
export function buildAnnouncementQueryParams(params = {}, toDate = value => (value ? String(value) : '-')) {
  const queryParams = {};

  // 1. 날짜 조건 매핑
  let startDate = params.startDate;
  let endDate = params.endDate;
  if (!startDate && params.startStr) startDate = toDate(params.startStr);
  if (!endDate && params.endStr) endDate = toDate(params.endStr);

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
  if (params.keyword) queryParams.keyword = params.keyword;
  // 주의: params.citys는 프론트엔드에서 실시간으로 정밀 필터링하므로 서버 keyword에 임의로 전달하지 않는다.
  // (서버 검색 컬럼이 houseNm 등으로 한정되어 있으면 hssplyAdres 검색 시 데이터가 통째로 누락되는 문제 방지)

  // 4. 페이징 (달력 및 리스트 조회를 위해 100건 기본)
  queryParams.page = params.page != null ? params.page : 0;
  queryParams.size = params.size != null ? params.size : 100;

  return queryParams;
}

/**
 * 목록 쿼리 키에 넣을 값. page를 지정하지 않으면 기간 안의 모든 페이지를 모아 오므로 그 여부도 키에 넣는다.
 */
export function announcementListKeyParams(params = {}, toDate) {
  return { ...buildAnnouncementQueryParams(params, toDate), allPages: params.page == null };
}
