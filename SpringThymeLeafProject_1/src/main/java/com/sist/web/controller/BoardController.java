package com.sist.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import com.sist.web.entity.*;
import com.sist.web.service.*;

import lombok.RequiredArgsConstructor;
@Controller
@RequiredArgsConstructor
@RequestMapping("board/") // 중복되는 경로명 
// JSP => HTML => 화면실행
// HTML => 화면 실행 -> 위에보다 빠름
public class BoardController {
    private final BoardService bService;
    
    @GetMapping("list") // board/list => *.do , /*
    public String board_list(@RequestParam(value= "page",required =false)String page,Model model)
    {
    	if(page==null)
    		page="1";
    	int curpage=Integer.parseInt(page);
    	int start=(curpage*10)-10;
    	List<BoardEntity> list=bService.boardListData(start);
    	int count=bService.boardCount();
    	int totalpage=(int)(Math.ceil(count/10.0));
    	
    	model.addAttribute("list",list);
    	model.addAttribute("curpage",curpage);
    	model.addAttribute("totalpage",totalpage);
    	return "board/list"; //list.html
    }
}
