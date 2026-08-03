package com.sist.web.service;
import java.util.*;

import org.springframework.stereotype.Service;

import com.sist.web.entity.*;
import com.sist.web.repository.*;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService{
   private final BoardRepository dao;

   @Override
   public BoardEntity findByNo(int no) {
	// TODO Auto-generated method stub
	return dao.findByNo(no);
   }

   @Override
   public List<BoardEntity> boardListData(int start) {
	// TODO Auto-generated method stub
	return dao.boardListData(start);
   }

   @Override
   public void boardUpdate(BoardEntity vo) {
	// TODO Auto-generated method stub
	dao.save(vo); // no에 값이 있는 경우
   }

   @Override
   public void boardInsert(BoardEntity vo) {
	// TODO Auto-generated method stub
	dao.save(vo); // no에 값이 없는 경우
   }

   @Override
   public void boardDelete(BoardEntity vo) {
	// TODO Auto-generated method stub
	dao.delete(vo);
   }

   @Override
   public int boardCount() {
	// TODO Auto-generated method stub
	return (int)dao.count(); // SELECT COUNT(*) FROM board
   }
}
