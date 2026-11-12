import { useEffect } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { CONTACT_EMAIL, EFFECTIVE_DATE, PRIVACY, TERMS } from './legalContent';
import { resetDefaultSeo, updatePageSeo } from '@/utils/seo.util';
import './legal.css';

const DOCS = {
  privacy: { title: '개인정보처리방침', lead: 'TOADY는 서비스 제공에 꼭 필요한 정보만 처리하고, 이용 통계는 회원과 연결하지 않습니다.', sections: PRIVACY, path: '/privacy' },
  terms: { title: '이용약관', lead: 'TOADY는 공공데이터를 바탕으로 청약 정보를 쉽게 찾도록 돕는 무료 참고 서비스입니다.', sections: TERMS, path: '/terms' }
};

/** 개인정보처리방침·이용약관. 푸터와 회원가입 화면에서 연결한다. */
export default function LegalPage({ kind }) {
  const doc = DOCS[kind];
  useEffect(() => {
    updatePageSeo({ title: `${doc.title} · TOADY`, description: doc.lead, path: doc.path });
    return () => resetDefaultSeo();
  }, [doc]); // eslint-disable-line react-hooks/exhaustive-deps
  return <article className="legal-page">
    <header>
      <span className="legal-eyebrow">TOADY 정책</span>
      <h1>{doc.title}</h1>
      <p>{doc.lead}</p>
      <nav aria-label="정책 문서"><RouterLink to="/privacy" className={kind === 'privacy' ? 'is-active' : ''}>개인정보처리방침</RouterLink><RouterLink to="/terms" className={kind === 'terms' ? 'is-active' : ''}>이용약관</RouterLink></nav>
    </header>
    {doc.sections.map(section => <section key={section.title}>
      <h2>{section.title}</h2>
      <ul>{section.items.map(item => <li key={item}>{item}</li>)}</ul>
    </section>)}
    <footer>시행일: {EFFECTIVE_DATE} · 문의 <a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a></footer>
  </article>;
}
