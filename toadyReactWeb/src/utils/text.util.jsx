import React from 'react';

/** 이메일 형식인지 간단히 검사한다(로그인·회원가입·비밀번호 찾기 공통). */
export function isEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(email).toLowerCase());
}

/**
 * 백엔드 서버에서 내려오는 메시지에 포함된 <br>, <br/>, \n 등의 개행 문자를
 * React <br /> 엘리먼트로 안전하게 분할 렌더링하는 헬퍼 함수
 * 
 * @param {string|any} msg
 * @returns {React.ReactNode}
 */
export const formatServerMessage = (msg) => {
  if (!msg || typeof msg !== 'string') return msg;
  const lines = msg.split(/<br\s*\/?>|\r?\n/gi);
  // 문자열 그대로 HTML로 넣지 않고(dangerouslySetInnerHTML 미사용) 줄마다 나눠 그려서 스크립트 삽입을 막는다.
  // 줄 순서가 바뀌지 않는 고정 목록이라 key로 인덱스를 써도 괜찮다.
  return lines.map((line, idx) => (
    <React.Fragment key={idx}>
      {line}
      {idx < lines.length - 1 && <br />}
    </React.Fragment>
  ));
};

