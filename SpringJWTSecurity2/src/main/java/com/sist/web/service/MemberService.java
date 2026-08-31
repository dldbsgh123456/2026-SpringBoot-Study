package com.sist.web.service;

import java.util.List;

import com.sist.web.vo.AuthorityVO;
import com.sist.web.vo.MemberVO;

public interface MemberService {
	public List<AuthorityVO> getAuthorityData(String userid);
	
	public MemberVO findByUserId(String userid);
}
