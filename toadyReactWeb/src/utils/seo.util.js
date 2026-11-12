import { announcementShareUrl, announcementShareImage, shareLabel } from '@/features/planner/shareUtils';
/**
 * @file seo.util.js
 * @description 구글 및 네이버 검색 로봇(SEO) 및 SNS 공유(OG/Twitter)를 위한 동적 메타 태그 & 구조화 데이터 제어 유틸리티
 */

const DEFAULT_SEO = {
  title: 'TOADY 두껍아 - 청약 일정, 분양 정보 및 부동산 커뮤니티',
  description: '전국 아파트, 오피스텔, 무순위(줍줍) 청약 일정, 분양가, 경쟁률, 당첨가점 및 실시간 청약 커뮤니티 플랫폼 TOADY(두껍아)',
  keywords: '청약, 아파트청약, 청약일정, 무순위, 줍줍, 분양가, 청약경쟁률, 당첨가점, 부동산커뮤니티, 두껍아, TOADY',
  url: 'https://toady.co.kr/',
  image: 'https://www.toady.co.kr/og/toady-og.png'
};

function setMetaContent(selector, content) {
  if (!content) return;
  const el = document.querySelector(selector);
  if (el) {
    el.setAttribute('content', content);
  }
}

function setLinkHref(selector, href) {
  if (!href) return;
  const el = document.querySelector(selector);
  if (el) {
    el.setAttribute('href', href);
  }
}

/**
 * 특정 공고 상세 열람 시 검색엔진 메타 태그 & Schema.org 구조화 데이터 동적 갱신
 * @param {object} detailData subscriptDetail 객체
 */
export function updateAnnouncementSeo(detailData,tab='detail',scoreText='') {
  if (!detailData || !detailData.houseNm) return;

  const houseNm = detailData.houseNm || '분양 공고';
  const location = detailData.supplyLocation || detailData.hssplyAdres || '';
  const scale = detailData.supplyScale || '';
  const date = detailData.subscriptDate || detailData.applicationDate || '';
  const seq = detailData.subscriptSeq || '';
  const fullUrl = announcementShareUrl(seq,tab);
  const canonicalUrl=announcementShareUrl(seq);
  const imageUrl=announcementShareImage(seq,tab);
  const title=`${houseNm} | ${tab==='rate' && scoreText?'청약 경쟁률·당첨가점':shareLabel(tab)} · TOADY`;

  // 1. 브라우저 탭 타이틀
  const pageTitle = title;
  document.title = pageTitle;

  // 2. 검색 요약 설명문 (Description)
  const pageDescription = `${houseNm} 청약 상세 안내. 위치: ${location}, 규모: ${scale}, 청약접수일: ${date}. 공고 일정과 공개된 경쟁률·당첨가점 확인.`;
  setMetaContent('#meta-description', pageDescription);
  setMetaContent('meta[name="description"]', pageDescription);

  // 3. 키워드 태그
  const pageKeywords = `${houseNm}, ${houseNm} 청약, ${houseNm} 분양가, ${location} 아파트, 청약 경쟁률, 당첨가점, TOADY`;
  setMetaContent('#meta-keywords', pageKeywords);

  // 4. OpenGraph (카카오톡, 네이버 카페/블로그 공유 시 카드)
  setMetaContent('#og-title', title);
  setMetaContent('#og-image',imageUrl);
  setMetaContent('meta[property="og:image"]',imageUrl);
  setMetaContent('meta[name="twitter:image"]',imageUrl);
  setMetaContent('#og-description', `${scoreText?scoreText+' | ':''}${location} | ${scale} | 접수: ${date}`);
  setMetaContent('#og-url', fullUrl);
  setMetaContent('meta[property="og:url"]', fullUrl);

  // 5. Twitter Card
  setMetaContent('#twitter-title', title);
  setMetaContent('#twitter-description', `${scoreText?scoreText+' | ':''}${location} | ${scale} | 접수: ${date}`);

  // 6. 검색엔진 캐노니컬 URL
  setLinkHref('#canonical-url', canonicalUrl);
  setLinkHref('link[rel="canonical"]', canonicalUrl);

  // 7. 구글/네이버 리치 검색 결과용 JSON-LD 구조화 데이터 주입
  const ldScript = document.getElementById('structured-data-script');
  if (ldScript) {
    const structuredData = {
      '@context': 'https://schema.org',
      '@type': 'RealEstateListing',
      name: houseNm,
      description: pageDescription,
      url: fullUrl,
      datePosted: detailData.applicationDate || undefined,
      validFrom: detailData.subscriptDate || undefined,
      address: {
        '@type': 'PostalAddress',
        streetAddress: location,
        addressCountry: 'KR'
      }
    };
    ldScript.textContent = JSON.stringify(structuredData);
  }
}

/**
 * 메인 달력 화면으로 복귀 시 기본 SEO 메타 태그 복원
 */
export function resetDefaultSeo() {
  document.title = DEFAULT_SEO.title;
  setMetaContent('#meta-description', DEFAULT_SEO.description);
  setMetaContent('meta[name="description"]', DEFAULT_SEO.description);
  setMetaContent('#meta-keywords', DEFAULT_SEO.keywords);

  setMetaContent('#og-title', DEFAULT_SEO.title);
  setMetaContent('meta[property="og:image"]',DEFAULT_SEO.image);
  setMetaContent('meta[name="twitter:image"]',DEFAULT_SEO.image);
  setMetaContent('#og-description', DEFAULT_SEO.description);
  setMetaContent('#og-url', DEFAULT_SEO.url);
  setMetaContent('meta[property="og:url"]', DEFAULT_SEO.url);

  setMetaContent('#twitter-title', DEFAULT_SEO.title);
  setMetaContent('#twitter-description', DEFAULT_SEO.description);

  setLinkHref('#canonical-url', DEFAULT_SEO.url);
  setLinkHref('link[rel="canonical"]', DEFAULT_SEO.url);

  const ldScript = document.getElementById('structured-data-script');
  if (ldScript) {
    ldScript.textContent = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'WebSite',
      name: 'TOADY 두껍아',
      url: DEFAULT_SEO.url,
      description: DEFAULT_SEO.description,
      potentialAction: {
        '@type': 'SearchAction',
        'target': 'https://toady.co.kr/?search={search_term_string}',
        'query-input': 'required name=search_term_string'
      }
    });
  }
}


/** 도구·안내 화면(예: 가점 계산기)의 제목·설명·대표 주소. 화면을 떠날 때 resetDefaultSeo로 되돌린다. */
export function updatePageSeo({ title, description, path }) {
  const url = new URL(path || '/', 'https://www.toady.co.kr').href;
  document.title = title;
  for (const selector of ['#meta-description', 'meta[name="description"]', '#og-description', '#twitter-description']) setMetaContent(selector, description);
  for (const selector of ['#og-title', '#twitter-title']) setMetaContent(selector, title);
  setMetaContent('#og-url', url);
  setMetaContent('meta[property="og:url"]', url);
  setLinkHref('#canonical-url', url);
  setLinkHref('link[rel="canonical"]', url);
}
