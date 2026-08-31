package com.sist.web.config;

import java.util.Date;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
	 // 실무에서는 자동 설정 => 키 설정 
	 // application => jwt: secret: ${JWT_SECRET}
     private final String SECRET="my-secret-key-my-secret-key--my-secret-key--my-secret-key";
     public String createToken(String username,String role) {
    	 /*
    	  *   PayLoad 
    	  *     {
    	  *       sub : "admoin" ,
    	  *       role : "ROLE_ADMIN" => token => byte
    	  *     }
    	  */
    	 return Jwts.builder()
    			.setSubject(username) // 사용자 ID 저장 {sub: admin}
    			.claim("role", role) // 권한 추가 => ROLE_ADMIN
    			.setIssuedAt(new Date()) // JWT 발급 시간 저장 
    			.setExpiration(new Date(System.currentTimeMillis()+3600000))
    			// 만료시간 등록 
    			// 3600000 => 1시간 (60분)  => 60*60*1000 => MS 
    			.signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
    			// SECRET key를 이용해서 JWT에 서명 
    			.compact();
     }
     public String getUsername(String token)
     {
    	 return Jwts.parserBuilder()
    			.setSigningKey(SECRET.getBytes())
    			.build()
    			.parseClaimsJws(token)
    			.getBody()
    			.getSubject();
     }
     public boolean validate(String token)
     {
    	 try
    	 {
    		 Jwts.parserBuilder()
    		 .setSigningKey(SECRET.getBytes())
    		 .build()
    		 .parseClaimsJws(token);
    		 return true;
    	 }catch(Exception ex)
    	 {
    		 return false;
    	 }
     }
     
}