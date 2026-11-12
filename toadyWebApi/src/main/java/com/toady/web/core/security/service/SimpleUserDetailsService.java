package com.toady.web.core.security.service;


import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.constants.AuthorityCd;
import com.toady.web.core.util.SecUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 로그인 검증.
 *
 * @author youn
 */
@Slf4j
@Component
public class SimpleUserDetailsService implements UserDetailsService {
	
	@Autowired
	private UserRepository userRepository;
	
	@Override
	public UserDetails loadUserByUsername(String userEmail)
			throws UsernameNotFoundException,
			       CredentialsExpiredException,
			       DisabledException,
			       AccountExpiredException,
			       LockedException
	{

		// 이메일로 계정을 조회하고, 잠금·탈퇴·이메일 미인증 상태를 로그인 전에 차단합니다.
		log.debug("계정 조회 [{}]", com.toady.web.core.util.CmnUtils.maskEmail(userEmail));
		UserEntity dbUserInfo = userRepository.findByUserEmail(userEmail);
		
		if (dbUserInfo == null) {
			log.info("NOT Found User"); //일치하는 정보가 없습니다.
			throw new UsernameNotFoundException("Email 또는 Password가 잘못되었습니다.");
		}

		if ("Y".equals(dbUserInfo.getLockYn())) { //계정 락 체크
			throw new LockedException("계정이 잠겨 있습니다.");
		}

		if ("Y".equals(dbUserInfo.getWithdrawalYn())) { //탈퇴 회원 체크
			throw new AccountExpiredException("탈퇴한 회원입니다.");
		}

		if(!"Y".equals(dbUserInfo.getEmailCertifyYn())){
			throw new DisabledException("인증 요청된 Email을 확인 해 주세요.<br/>인증 후 로그인 가능합니다.");
		}

		// 현재 사용자 권한을 구성해 인증 객체가 사용할 사용자 상세정보를 만듭니다.
		List<String> authList = new ArrayList();
		authList.add(AuthorityCd.USER.roleCd()); //권한그룹이 존재하니 임의로 ADMIN을 넣어줌.

		CustomUserDetails user = new CustomUserDetails(dbUserInfo.getUserEmail(), dbUserInfo.getUserPassword(), SecUtils.mapToGrantedAuthorities(authList));
		user.setUserSeq(dbUserInfo.getUserSeq());
		user.setUserNicName(dbUserInfo.getUserNicName());

		return user;
	}
}
