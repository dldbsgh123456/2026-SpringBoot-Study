package com.sist.web.vo;

import lombok.Data;

/*
 *   USERID               VARCHAR2(20)
 *   AUTHORITY NOT NULL   VARCHAR2(20)
 * 
 */
@Data
public class AuthorityVO {
     private String userid;
     private String authority; // 권한 => ROLE_ADMIN
}
