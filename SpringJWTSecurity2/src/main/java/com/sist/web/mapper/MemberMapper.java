package com.sist.web.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;
/*
 *    자바
 *     	Wrapper 클래스
 *     	제네릭
 *     	컬렉션
 *     	예외처리 종류
 *    자바스크립트
 *     	클로저 (상속) / 호이스팅 (변수 초기화)
 *    오라클
 *      JOIN / SubQuery
 *   ---------------------
 *    Redis / React 
 * 
 */

import com.sist.web.vo.MemberVO;
@Mapper
@Repository
public interface MemberMapper {
	@Select("SELECT userid,username,userpwd,enable,sex "
		   +"FROM member springmember "
		   +"WHERE userid=#{userid}")
	public MemberVO findByUserId(String userid);
}
