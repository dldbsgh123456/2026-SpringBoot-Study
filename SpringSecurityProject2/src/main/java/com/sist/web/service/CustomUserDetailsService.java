package com.sist.web.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sist.web.mapper.UserMapper;
import com.sist.web.vo.MemberVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService{
	private final UserMapper mapper;
	
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		// TODO Auto-generated method stub
		MemberVO user=mapper.findByUserid(username);
		if(user==null)
		{
			throw new UsernameNotFoundException("UserName을 찾을 수 없습니다");
		}
		List<String> roles=mapper.findRolesByUserid(username);
		// 권한 저장
		Set<GrantedAuthority> authorities=new HashSet<>();
		for(String role:roles)
		{
			authorities.add(new SimpleGrantedAuthority(role));
		}
		
		System.out.println("ID:"+user.getUsername());
		System.out.println("PW:"+user.getUserpwd());
		System.out.println(roles);
		return new User(user.getUsername(),user.getUserpwd(),user.getEnable()==0?false:true,true,true,true,authorities);
	}
	
	/*
	 *    UserDetails
	 * 
	 *    1. user.getUsername() id
	 *    2. user.getUserpwd() password
	 *    3. user.getEnable() => 활성화 여부
	 *    4. true : 계정 만료 여부
	 *    5. true : 계정 잠금 여부
	 *    6. true : 비밀번호 만료 여부
	 *    7. authorities : 권한 정보
	 * 
	 *   => Principal
	 */
        

}
