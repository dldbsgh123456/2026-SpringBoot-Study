package com.sist.web.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
      @Bean
      public SecurityFilterChain filterChain(HttpSecurity http) throws Exception
      {
    	  /*
    	   *  접근 권한
    	   *  로그인
    	   *  로그아웃
    	   *  자동로그인    	        	   
    	   *  
    	   *  Controller
    	   *      |
    	   *  SecurityConfig => URL별 권한 설정
    	   *      |
    	   *  CustomUserDetailsService = 사용자 정보 / 권한 정보
    	   *  
    	   *  => ${}   th:each="vo:${list}" [[${vo.name}]]
    	   *  
    	   *  => ${sessionScope.id} -> jsp
    	   *     =>${session.id} -> thymeleaf
    	   *  
    	   *  
    	   *   authority : 권한
    	   *   springmember : enable / userid username userpwd 
    	   */
    	  // 1. 인증 => 권한 부여
    	  http
    	     .csrf(csrf->csrf.disable()) // 위조 => 방지
    	     .authorizeHttpRequests(auth -> auth
    	     .requestMatchers("/","/login").permitAll()    	     
    	     .requestMatchers("/user").authenticated()
    	     .requestMatchers("/admin").hasRole("ADMIN")
    	     .anyRequest().permitAll()
    	     /*
    	      *   authenticated() : 로그인 된 상태
    	      * 
    	      */
    	    )
    	     // 2. 로그인
    	     .formLogin(form->form
    	          .loginPage("/login")
    	          .loginProcessingUrl("/login_process") // SpringSecurity에서 /login POST => Controller 처리가 아니라 security에서 인터셉트로 처리하는 방식
    	          .defaultSuccessUrl("/",true)
    	          .failureUrl("/login?error")
    	          .permitAll()
    	     )
    	     // 3. 로그아웃
    	     .logout(logout->logout
    	          .logoutSuccessUrl("/")
    	     );
    	     // 자동 로그인
    	  return http.build();
    	  
    	  // PasswordEncoding => 암호화 => {noop}
      }
}
