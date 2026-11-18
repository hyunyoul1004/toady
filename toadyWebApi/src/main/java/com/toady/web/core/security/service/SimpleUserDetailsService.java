package com.toady.web.core.security.service;


import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.constants.AuthorityCd;
import com.toady.web.core.util.CmnUtils;
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
 * 이메일로 TB_USER를 조회해 CustomUserDetails를 만든다. 잠금·탈퇴·이메일 미인증이면 예외로 막는다.
 * 비밀번호 비교는 여기서 하지 않고 UserService가 PasswordEncoder로 한다.
 * UserService(로그인), AccountService(토큰 재발급)가 직접 부르고, SecurityConfig에도 등록되어 있다.
 *
 * @author youn
 */
// @Slf4j: log 필드 생성
@Slf4j
// @Component: 빈으로 등록한다. SecurityConfig가 생성자 주입으로 받는다
@Component
public class SimpleUserDetailsService implements UserDetailsService {
	
	// @Autowired: UserRepository 빈을 필드에 주입한다
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
		log.debug("계정 조회 [{}]", CmnUtils.maskEmail(userEmail));
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
		authList.add(AuthorityCd.USER.roleCd()); //일반 회원은 모두 USER 권한 하나만 준다.

		CustomUserDetails user = new CustomUserDetails(dbUserInfo.getUserEmail(), dbUserInfo.getUserPassword(), SecUtils.mapToGrantedAuthorities(authList));
		user.setUserSeq(dbUserInfo.getUserSeq());
		user.setUserNicName(dbUserInfo.getUserNicName());

		return user;
	}
}
