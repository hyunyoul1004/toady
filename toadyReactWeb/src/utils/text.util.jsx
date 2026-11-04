import React from 'react';

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
  return lines.map((line, idx) => (
    <React.Fragment key={idx}>
      {line}
      {idx < lines.length - 1 && <br />}
    </React.Fragment>
  ));
};

