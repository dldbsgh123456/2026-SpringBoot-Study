package com.sist.web.controller;


import java.util.Iterator;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sist.web.config.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor // 반드시 스프링에서 메모리 할당이 된 경우만 사용할 수 있음
public class AuthController {
   private final AuthenticationManager manager;
   private final JwtTokenProvider provider;
   
   @RequestMapping("/member/login")
   public ResponseEntity<?> login(
      @RequestParam(value="username",required = false) String username,
      @RequestParam(value="password",required = false) String password
   )
   {
	   System.out.println("username:"+username);
	   System.out.println("password:"+password);
	   Authentication auth=
			   manager.authenticate(
				  new UsernamePasswordAuthenticationToken(username, password)	   
			   );
	   UserDetails user=
			   (UserDetails)auth.getPrincipal();
	   System.out.println(user);
	   String token=provider.createToken(user.getUsername(),
			   user.getAuthorities().iterator().next().getAuthority());
	   System.out.println(token);
	   ResponseCookie cookie =
	            ResponseCookie.from(
	                    "accessToken",
	                    token
	            )
	            .httpOnly(true)
	            .secure(false)
	            .path("/")
	            .maxAge(3600)
	            .build();
        System.out.println(cookie);
	    return ResponseEntity
	            .status(HttpStatus.FOUND)
	            .header(
	                HttpHeaders.SET_COOKIE,
	                cookie.toString()
	            )
	            .header(
	                HttpHeaders.LOCATION,
	                "/home"
	            )
	            .build();
   }
}