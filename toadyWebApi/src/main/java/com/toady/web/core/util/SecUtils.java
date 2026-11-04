package com.toady.web.core.util;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시큐리티 관련 유틸 클래스
 *
 * @author youn
 */
public class SecUtils {

	/**
	 * 유저 이름 가져오기
	 *
	 * @return
	 */
	public static String getUserEmail() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return "";
		}

		return authentication.getName();
	}

	/**
	 * Convert
	 *  - String -> Delete<GrantedAuthority>
	 *
	 * @param authoritiesStr
	 * @return
	 */
	public static List<GrantedAuthority> mapToGrantedAuthorities(String authoritiesStr) {
		return mapToGrantedAuthorities(Arrays.asList(authoritiesStr.split(",")));
	}

	/**
	 * Convert
	 *  - Delete<String> -> Delete<GrantedAuthority>
	 *
	 * @param authorities
	 * @return
	 */
	public static List<GrantedAuthority> mapToGrantedAuthorities(List<String> authorities) {
		return authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
	}

	/**
	 * 문자열로 가져오기
	 *
	 * @param authorities
	 * @return
	 */
	public static String getAuthorities(Collection<? extends GrantedAuthority> authorities) {
		return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","));
	}
	
	/**
	 * mariadb password 함수 구현.
	 * @param inpara
	 * @return 암호화된 password
	 */
	public static String mysqlPassword(String inpara) {
		byte[] bpara = new byte[inpara.length()];
		byte[] rethash;
		int i;
		
		for (i=0; i < inpara.length(); i++) {
			bpara[i] = (byte)(inpara.charAt(i) & 0xff );
		}
		
		try {
			MessageDigest sha1er = MessageDigest.getInstance("SHA1");
			rethash = sha1er.digest(bpara); // stage1
			rethash = sha1er.digest(rethash); // stage2
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		
		StringBuffer r = new StringBuffer(41);
		r.append("*");
		
		for (i=0; i < rethash.length; i++) {
			String x = Integer.toHexString(rethash[i] & 0xff).toUpperCase();
			
			if (x.length() < 2)
				r.append("0");
				r.append(x);
		}
		
		return r.toString();
	}
}
