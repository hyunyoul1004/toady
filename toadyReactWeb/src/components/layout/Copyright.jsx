import React from 'react';
import { Link as RouterLink } from 'react-router-dom';
import './footer.css';

/**
 * 사이트 푸터: 서비스 소개, 데이터 출처, 정책 문서, 문의. 처음 온 사용자가 운영 주체와 정보 출처를 확인할 수 있게 한다.
 * 모든 링크는 앱 안 화면으로 연다(검색용 서버 페이지 /zupzup·/weekly·/areas와 같은 내용).
 */
function Copyright() {
  return (
    <div className="site-footer">
      <div className="site-footer-brand">
        <strong>TOADY</strong>
        <p>청약 공고 찾기부터 경쟁률·당첨가점, 위치, 동네 실거래까지 한 화면에서 확인하는 청약 정보 서비스</p>
      </div>
      <nav className="site-footer-links" aria-label="사이트 바로가기">
        <RouterLink to="/">청약 캘린더</RouterLink>
        <RouterLink to="/match">맞춤 청약</RouterLink>
        <RouterLink to="/score">가점 계산기</RouterLink>
        <RouterLink to="/match/zupzup">무순위·줍줍</RouterLink>
        <RouterLink to="/this-week">이번 주 청약</RouterLink>
        <RouterLink to="/regions">지역별 청약</RouterLink>
        <RouterLink to="/faq">자주 묻는 질문</RouterLink>
      </nav>
      <nav className="site-footer-links is-policy" aria-label="정책">
        <RouterLink to="/privacy"><b>개인정보처리방침</b></RouterLink>
        <RouterLink to="/terms">이용약관</RouterLink>
        <a href="mailto:dukkeoba@gmail.com">문의 dukkeoba@gmail.com</a>
      </nav>
      <p className="site-footer-note">청약홈·국토교통부 공개 데이터를 바탕으로 한 참고 정보입니다. 청약 신청과 자격 확인은 청약홈과 공식 모집공고에서 해 주세요.</p>
      <p className="site-footer-copy">© {new Date().getFullYear()} TOADY</p>
    </div>
  );
}

export default Copyright;
