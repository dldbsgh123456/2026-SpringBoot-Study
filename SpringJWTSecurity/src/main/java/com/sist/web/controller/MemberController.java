package com.sist.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
// Router : 화면만 이동 => @RestController (Front-End)
public class MemberController {
	@GetMapping("/")
	public String home()
	{
		return "home";
	}
	
	@GetMapping("/user")
	public String user()
	{
		return "user";
	}
	
	@GetMapping("/admin")
	public String admin()
	{
		return "admin";
	}
	
	@GetMapping("/login")
	public String login()
	{
		return "login";
	}
}
