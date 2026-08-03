package com.sist.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import com.sist.web.entity.*;
import com.sist.web.service.*;
import com.sist.web.vo.BoardDTO;

import lombok.RequiredArgsConstructor;
@Controller
@RequiredArgsConstructor
@RequestMapping("board/") // 중복되는 경로명 
// JSP => HTML => 화면실행
// HTML => 화면 실행 -> 위에보다 빠름
/*
 *   값을 받는 경우 @RequestParam을 반드시 이용
 *   null 값이 있는 경우 required = false
 *   Spring Framework와 동일
 *  
 *    동작 순서
 *    -------
 *    브라우저
 *      | ==== 요청 (<a> , <form>)
 *   Spring Container => DispatcherServlet => 처리
 *      | ==== Model에 데이터 전달
 *   ThymeLeaf의 엔진
 *      | ==== 
 *   완성된 HTML
 *      |
 *   브라우저 출력
 *   
 *   Spring 통합 => @Controller , Model , @RequestParam
 *   표현식 지원 => ${} , @{} , *{}
 *               ---------
 *   객체 : #number => 페이지를 나눠서 처리
 *   
 *   디렉티브 : th:
 *      th:text : 데이터 출력
 *      th:utext : => html을 출력
 *      th:each : for문
 *      th:if : 조건문
 *      -----  th:unless (== else)
 *      th:href => link , th:src , th:value , th:action
 *      
 *      객체
 *       => #datas : 날짜 변환
 *          <span th:text="${#datas.format(date,"yyyy-MM-dd")}">
 *       => #strings : 문자열 제어
 *          <span th:text="${#strings.length(name)}">
 *       => #lists : list 출력
 *       => #numbers : 숫자
 *       => @authentication : 보안
 *      
 * 
 */
public class BoardController {
    private final BoardService bService;
    
    @GetMapping("list") // board/list => *.do , /*
    public String board_list(@RequestParam(value= "page",required =false)String page,Model model)
    {
    	if(page==null)
    		page="1";
    	int curpage=Integer.parseInt(page);
    	int start=(curpage*10)-10;
    	List<BoardDTO> list=bService.boardListData(start);
    	int count=bService.boardCount();
    	int totalpage=(int)(Math.ceil(count/10.0));
    	
    	model.addAttribute("list",list);
    	model.addAttribute("curpage",curpage);
    	model.addAttribute("totalpage",totalpage);
    	return "board/list"; //list.html
    }
    
    @GetMapping("detail")
    public String board_detail(@RequestParam("no") int no, Model model)
    {
    	BoardEntity vo=bService.findByNo(no);
    	// SELECT * FROM board WHERE no=?
    	vo.setHit(vo.getHit()+1);
    	bService.boardUpdate(vo); // 조회수 증가 
    	
    	vo=bService.findByNo(no);
    	model.addAttribute("vo",vo);
    	return "board/detail";
    }
    
    @GetMapping("insert")
    public String board_insert()
    {
    	return "board/insert";
    }
    
    @PostMapping("insert_ok")
    public String board_insert_ok(@ModelAttribute("vo") BoardEntity vo)
    {
    	bService.boardInsert(vo);
    	return "redirect:/board/list";
    }
    
    @GetMapping("delete")
    public String board_delete(@RequestParam("no") int no,Model model)
    {
    	model.addAttribute("no",no);
    	return "/board/delete";
    }
    
    @PostMapping("delete_ok")
    public String board_delete_ok(@RequestParam("no") int no,@RequestParam("pwd") String pwd,Model model)
    {
    	String res="no";
    	BoardEntity vo=bService.findByNo(no);
    	if(vo.getPwd().equals(pwd))
    	{
    		res="yes";
    		bService.boardDelete(vo); // delete()
    	}
    	model.addAttribute("res",res);
    	return "/board/delete_ok";
    }
    
    @GetMapping("update")
    public String board_update(@RequestParam("no") int no,Model model)
    {
    	BoardEntity vo=bService.findByNo(no);
    	model.addAttribute("vo",vo);
    	return "/board/update";
    }
    // 서버는 동일
    // 데이터베이스 => MyBatis => JPA
    // 화면 출력 => JSP => ThymeLeaf
    // 배포  => Maven => Gradle : 배포가 편함
    // Spring / SpringBoot
    //           | 내장 Tomcat
    // | 외장 Tomcat
    // Front => EL 사용이 동일 = 문법사항
    // => Vue
    // MVC의 동작 구조는 동일
    // => SpringBoot는 서버로 특화
    @PostMapping("update_ok")
    public String board_update_ok(@ModelAttribute("vo") BoardEntity vo,Model model)
    {
    	BoardEntity dbvo=bService.findByNo(vo.getNo());
    	String res="no";
    	if(vo.getPwd().equals(dbvo.getPwd()))
    	{
    		vo.setNo(vo.getNo());
    		vo.setHit(dbvo.getHit());
    		bService.boardUpdate(vo);
    		res="yes";
    	}
    	model.addAttribute("res",res);
    	model.addAttribute("no",vo.getNo());
    	return "/board/update_ok";
    }
    
}
