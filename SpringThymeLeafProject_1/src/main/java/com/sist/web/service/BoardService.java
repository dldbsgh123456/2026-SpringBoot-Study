package com.sist.web.service;
import java.util.*;

import org.springframework.data.repository.query.Param;

import com.sist.web.entity.*;
import com.sist.web.vo.BoardDTO;
public interface BoardService {
    public BoardEntity findByNo(int no);
    public List<BoardDTO> boardListData(int start);
    public void boardUpdate(BoardEntity vo);
    public void boardInsert(BoardEntity vo);
    public void boardDelete(BoardEntity vo);
    public int boardCount();
}
