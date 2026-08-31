package com.sist.web;

import java.util.Arrays;
/*
 *    클래스별 역할 / 동작 순서 / 소스 분석
 *    1) 전체 동작 순서
 *       => 로그인 = 인증
 *    1. 로그인 요청 : POST => /member/login
 *                            | id , pwd
 *    
 *    2. AuthController
 *         => AuthenticationManager 인증의 위임
 *         => AuthenticationManager는 UserDetailsService 통해
 *            데이터베이스에서 사용자 검색 => 비번/아이디 일치 확인
 *    3. JWT 발급 / 쿠키에 저장
 *       => JwtTokenProvider 
 *          => 1) 인증 성공 여부 => 아이디 + 권한을 포함한 데이터를 JWT 토큰에 추가
 *             2) JWT의 토큰을 Cookie에 저장 
 *             3) /home으로 이동
 *    4. JwtAuthenticationFilter
 *          => 다른 페이지를 요청시
 *             브라우저는 쿠키 , JWT Header
 *                             ------------ 자바스크립트
 *                 | 쿠키 읽어서 acsessToken 추출
 *                   => UserDetailsService 정보 읽어서
 *                   => SecurityContextHolder에 저장
 *    5. 컨트롤러 접근 완료
 *    
 *    -------------------------------------------------------------------------
 *    AuthController : 로그인 요청시에 => 인증 => JWT 토큰 생성 / 쿠키 발급
 *    JwtTokenProvider : JWT 토큰을 생성 , 위조 여부 확인
 *                                        --------------
 *                                        validate()
 *                       => 사용자의 아이디 / 권한 추출
 *                       => 데이터베이스 연동  
 *    JwtAuthenticationFilter
 *                       => JWT 토큰을 찾아서 유효한지 검사
 *                       	스프링 시큐리티 로그인 상태 등록
 *    JwtSecurityConfig => 페이지 규칙 설정 => 일반 security와 동일 
 * 
 * 
 */
public class MainClass {
         public static void main(String[] args) {
        	 String username="admin";
        	 byte[] bytes=username.getBytes();
        	 System.out.println(Arrays.toString(bytes));
		}
}
