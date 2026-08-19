package com.sist.web.vo;

import lombok.Data;

/*
 * USERID   NOT NULL VARCHAR2(20)  
	USERNAME NOT NULL VARCHAR2(50)  
	USERPWD  NOT NULL VARCHAR2(300) 
	ENABLE            NUMBER(1)     
	SEX               VARCHAR2(6)
 * 
 * 
 */
@Data
public class MemberVO {
     private int enable; // 휴먼 계정 
     private String userid,username,userpwd,sex;
}
