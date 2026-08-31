package com.sist.web.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sist.web.mapper.AuthorityMapper;
import com.sist.web.mapper.MemberMapper;
import com.sist.web.vo.AuthorityVO;
import com.sist.web.vo.MemberVO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService{
	private final MemberMapper mMapper;
	private final AuthorityMapper aMapper;
	@Override
	public List<AuthorityVO> getAuthorityData(String userid) {
		// TODO Auto-generated method stub
		return aMapper.getAuthorityData(userid);
	}
	@Override
	public MemberVO findByUserId(String userid) {
		// TODO Auto-generated method stub
		return mMapper.findByUserId(userid);
	}
}
