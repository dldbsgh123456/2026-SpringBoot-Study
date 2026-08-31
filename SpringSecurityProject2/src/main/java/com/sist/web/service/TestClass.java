package com.sist.web.service;
/*
 *   인증 처리
 *   1. 사용자가 로그인 요청
 *       => username(ID),password(pwd)
 *      => formlogin 에서 값 받음
 *   2. UsernamePasswordAuthenticationFilter 요청을 인터셉트
 *   3. UsernamePasswordAuthenticationToken(인증용 객체 생성)
 *   4. 토큰 => AuthenticationManager에 전송
 *                    |
 *              AuthenticationProvider
 *                    |
 *              UserDetailsService를 통해서 DB 에서 사용자 정보 조회
 *                    |
 *              조회된 데이터 (사용자 정보) => id 비번 확인 
 *                            ----------
 *                           UserDetails
 *   5. 인증 성공 => SecurityContext 에 저장
 *                   | Principal
 *   => UserDetails
 *   => UserDetailsService
 *   => PasswordEncoder
 * 
 */
public class TestClass {

}
