package com.sist.web.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
@Entity // 오라클 컬럼과 매칭이 된다
@Table(name="board") // 테이블 명칭과 같아야 함
@DynamicUpdate // 필요 시에 업데이트 설정
@Data
// => save(vo) 객체(Entity) === column연결
/*
 *    JPA (Java Persistence API)
 *    자바 객체와 데이터베이스의 데이터를 연결해주는 ORM(Object Relation Member) 표준 기술
 *    -------  --------------- 컬럼
 *       |              |
 *       ----------------
 *             | 자동 SQL문장 제작
 *             
 *    기존 
 *     Java Object
 *          |---------------- SQL을 직접 만들어서 처리
 *      오라클 테이블 연결
 *    
 *    JPA
 *     Java Object
 *          |---------------- JPA를 이용해서 자동 SQL문장 생성
 *     오라클 테이블 연결
 *     
 *     데이터베이스 테이블
 *     ---------------
 *      id    name  age
 *      
 *     => @Entity
 *        public class Member
 *        {
 *            @Id
 *            private String id ....
 *            private String name;
 *            private int age
 *        }
 *        
 *        @Entity // 오라클 column과 매칭
 *        @Table(name="board") : 테이블명 / 클래스 불일치
 *        @Dyna
 * 
 */
public class BoardEntity {
	 @Id  // 자동 증가 컬럼 => 자동으로 SQL문장을 제작
     private int no;
	 private String name,subject,content;
	 @Column(insertable = true,updatable = false)
	 private String pwd;
	 private int hit;
	 @Column(insertable = true,updatable = false)
	 private String regdate;
	 
	 @PrePersist // 날짜 변환
	 public void regdate() {
		 this.regdate=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
	 }
}
