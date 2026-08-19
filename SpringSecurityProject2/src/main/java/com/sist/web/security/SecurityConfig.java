package com.sist.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import com.sist.web.service.CustomUserDetailsService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	private final CustomUserDetailsService userDetailsService;
	// 재정의 : 권한에 따라 접근여부 확인 / 로그인 / 로그아웃 / 자동 로그인 
	// => 소셜 로그인 (KaKao,naver,google)
   // 1. URL별 권한 부여
   // 1-1 CSRF 처리 => 공격자가 인증된 브라우저에서 저장된 쿠키나 세션정보를 활용해서 => 다른 요청 전송 / 위조 방지
   //     csrf.disable() => Cross site Request Forgery
   // 1-2 authorizeHttpRequests: 인증, 인가가 필요한 URL 지정
   // 1-3 anyRequest() / requestMathcers => 지정된 URL에 권한 부여
   //     | permitALl()  / denyALL() => 접근 거부 => 403
   // 1-4 authenticated() : 인증이 된 사람
   // 1-5 hasRole(한개) , hasAnyRoles("","","")
   // 2. formLogin
   // 3. logout
   // 4. remember-me
   // SecurityFilterChain
   @Bean
   public SecurityFilterChain filterChain(HttpSecurity http) throws Exception
   {
	   // URL별 권한 부여
	   http
	      .csrf(csrf -> csrf.disable())
	      .authorizeHttpRequests(auth->auth
	          .requestMatchers("/","/join","/login").permitAll()
	          .requestMatchers("/user").authenticated()
	          .requestMatchers("/admin").hasRole("ADMIN")
	          .anyRequest().permitAll() // 게스트 포함
	      )
	      // 로그인
	      .formLogin(form->form 
	          .loginPage("/login") // 자체 로그인페이지로 감 이거 안 쓰면
	          .loginProcessingUrl("/login_process")
	          .defaultSuccessUrl("/",true)
	          .failureHandler(loginFailHandler())
	      )
	      // 로그아웃
	      .logout(logout->logout
	          .logoutSuccessUrl("/")
	      );
	      // 자동 로그인
	      return http.build();
   }
   // 5. 비밀번호 암호화
   @Bean
   public PasswordEncoder passwordEncoder()
   {
	   return new BCryptPasswordEncoder();
   }
   // 6. 로그인 성공/실패 
   @Bean
   public AuthenticationFailureHandler loginFailHandler()
   {
	   return new LoginFailHandler();
   }
}
