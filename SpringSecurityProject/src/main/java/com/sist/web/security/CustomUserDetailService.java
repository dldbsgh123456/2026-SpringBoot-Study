package com.sist.web.security;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.*;
public class CustomUserDetailService implements UserDetailsService{

	@Override
	public UserDetails loadUserByUsername(String username) {
		// TODO Auto-generated method stub
		if(username.equals("admin"))
		{
            return User.builder().username("admin").password("{noop}1234").roles("ADMIN").build();
		}
		return User.builder().username("user").password("{noop}1234").roles("USER").build();
	}

}
