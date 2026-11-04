import React from 'react';
import { Route } from 'react-router-dom';
import { Main } from 'views/main';
import {SignUpCertify}  from 'views/signup';

function Contents(props){
  const { match } = props;
  
  return (
    <>
      <Route exact path={`${match.path}certify`} component={SignUpCertify} /> {/** 회원가입 이메일 인증 */}
      <Route exact path={`${match.path}`} component={Main} />
    </>
  )
}

export default Contents;
