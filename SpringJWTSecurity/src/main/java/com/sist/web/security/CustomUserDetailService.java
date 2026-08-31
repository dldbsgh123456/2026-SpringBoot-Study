package com.sist.web.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
// 사용자 조회 (데이터베이스)
@Service
public class CustomUserDetailService implements UserDetailsService{

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		// TODO Auto-generated method stub
		// 임시 => MyBatis => JDBC 
		if(username.equals("admin")) {
			return User.builder()
				       .username("admin")
				       .password("{noop}1234")
				       .roles("ADMIN")
				       .build();
		}
		// {noop} => 암호화 없이 => Spring5 => 반드시 암호화 시켜야 함
		// BCryptPasswordEncoder
		// => encode() / match()
		//    암호화      복호화
		// 같은 비밀번호가 있는 경우 => 패턴 여러개 => 다르다
		return User.builder()
			       .username("user")
			       .password("{noop}1234")
			       .roles("USER")
			       .build();
	}

}
