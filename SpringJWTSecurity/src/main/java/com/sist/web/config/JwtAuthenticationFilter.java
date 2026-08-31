package com.sist.web.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
/*
 *   1.  동작 순서 
 *   2.  JWT 개념 
 *   3.  SpringSecurity 동작 
 *   4.  각 클래스의 역할 
 *       AuthenticationFilter / AuthenticationProvider
 *       JwtSecurityConfig 
 *   5.  Conttroller = ThymeLeaf 
 *   
 *   ------------------------------------------------
 *   1. 동작 순서 
 *        |
 *      로그인 요청 
 *        | = POST : /member/login 
 *      AuthController 
 *        | = AuthenticationManager.authenticate()
 *          = 인증 여부 확인 
 *      CustomUserDetailsService 
 *        | = 사용자 검색 
 *      UserDetails
 *        | = 저장 => 인증 성공 
 *      Authentication 성공 
 *        |
 *      AuthenticationProvider 
 *        | = createToken()
 *      JWT 발급 => 기간 => Cookie에 저장 
 *        |
 *      브라우저 
 *        |
 *      JwtAuthenticationFilter 
 *        | = Authentication 헤더 확인 
 *        | = 토큰 추출 
 *        | = JWT 검증 
 *        | = username 추출 
 *        | = UserDetails 조회 
 *        | = SecurityContext 정보 저장 
 *      Controller에 접근 
 *      ------------------------------
 *      JWT (JSON WEB Token) 
 *      xxxxx.yyyyy.zzzzz
 *                  | Signature
 *            | Payload 
 *              (실제 정보 저장)
 *      | Header 
 *      
 *      1. Spring Security 
 *         => session 방식 
 *         로그인 
 *           |
 *        ID / PW 확인 
 *           |
 *        Session 생성 
 *           |
 *        JSESSIONID 쿠키 저장 
 *           |
 *         다음 요청 
 *           |
 *         Session 확인 
 *           |
 *          로그인 사용자 확인 
 *            => 서버가 로그인 상태를 가지고 있다 
 *      2. JWT 
 *          로그인 
 *            |
 *          ID/PW 확인 
 *            |
 *          JWT 생성 
 *            |
 *          클라이언트가 JWT 저장 
 *            |
 *           다음 요청 
 *            |
 *          Authorization : Bearer JWT 
 *            |
 *          서버가 JWT 검증 
 *            |
 *          로그인 사용자 확인 
 *            => 세션없이 쿠키만 사용 
 *        
 */
// 사용자 요청시마다 JWT 토큰을 검사 => OncePerRequestFilter 요청당 한 번만 실행됨
public class JwtAuthenticationFilter 
extends OncePerRequestFilter
{
   // 사용자 정보를 데이터베이스를 연동해서 데이터 추출 
   private final UserDetailsService userDetailsService;
   private final JwtTokenProvider provider;     
   // JWT 토큰을 만들어서 검증하는 역할 수행  
   public JwtAuthenticationFilter(         // 생성자 호출 해야 돼서 @RequiredArgsConstrucor 안 씀?
		   UserDetailsService userDetailsService,
		   JwtTokenProvider provider   
   )
   {
	   this.userDetailsService=userDetailsService;
	   this.provider=provider;
   }
   // 실제 요청시마다 처리하는 메소드
	@Override
	protected void doFilterInternal(HttpServletRequest request, 
			HttpServletResponse response, 
			FilterChain filterChain)
			throws ServletException, IOException {
		// JWT 토큰을 저장할 변수 
		String token = null;
	    // Authorization : Bearer ecefdfcdsscdscsc
		// 1. Authorization Header 확인
		String header = request.getHeader("Authorization");
        System.out.println("header="+header);
		if (header != null && header.startsWith("Bearer ")) {

		    token = header.substring(7);
		}

		// 2. Header가 없으면 Cookie 확인
		if (token == null && request.getCookies() != null) {

		    for (Cookie cookie : request.getCookies()) {

		        if ("accessToken".equals(cookie.getName())) {

		            token = cookie.getValue();
		            System.out.println("token="+token);
		            break;
		        }
		    }
		}

		// 3. JWT가 존재하면 인증
		// => 1. 유효기간 / 사용자 추출 
		if (token != null && provider.validate(token)) {

		    String username =
		            provider.getUsername(token);

		    UserDetails user =
		            userDetailsService
		                    .loadUserByUsername(username);

		    UsernamePasswordAuthenticationToken auth =
		            new UsernamePasswordAuthenticationToken(
		                    username,
		                    null,
		                    user.getAuthorities()
		            );

		    SecurityContextHolder
		            .getContext()
		            .setAuthentication(auth);
		}
		// 정상적으로 컨트롤러가 다음을 수행할 수 있게 처리
		filterChain.doFilter(request, response);
	}

}