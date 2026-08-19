package com.sist.web.entity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Data;
@Entity // 오라클 column과 매칭 
@Table(name="jpaboard") 
@DynamicUpdate // 필요시에 업데이트 설정 
@Data
// => save(vo) 객체(Entity) === column연결 
/*
 *    JPA (Java Persistence API)
 *    자바 객체와 데이터베이스의 데이터를 연결해주는 ORM 표준 기술 
 *    -------  ---------------- 컬럼
 *      |              |
 *      ----------------
 *         | 자동 SQL문장 제작 
 *         
 *    기존 
 *      Java Object 
 *          |---------------- SQL을 직접 만들어서 처리 
 *      오라클 테이블 연결 
 *    JPA 
 *      Java Object 
 *         | --------------- JPA를 이용해서 자동 SQL문장 생성 
 *     오라클 테이블 연결
 *     
 *    데이터베이스 테이블 
 *    ---------------
 *     id    name  age 
 *     
 *    => @Entity
 *       public class Member
 *       {
 *           @Id
 *           private String id ....
 *           private String name;
 *           private int age 
 *       }
 *       
 *       @Entity // 오라클 column과 매칭 
         @Table(name="board") : 테이블명 / 클래스 불일치 
         @DynamicUpdate // 필요시에 없데이터 설정
         
 *       
 */
@DynamicInsert
@SequenceGenerator(
  name="jpb_no_seq",
  sequenceName = "jpb_no_seq",
  allocationSize = 1
)

// Entity == Column : 설정이 종료 
public class BoardEntity {
   @Id  // 자동 증가 컬럼 => 자동으로 SQL문장을 제작 
   // 시퀀스 
   @GeneratedValue(strategy = GenerationType.SEQUENCE,generator = "jpb_no_seq")
   private int no;
   private String name,subject,content;
   @Column(insertable = true,updatable = false)
   private String pwd;
   
   @ColumnDefault("0")
   private int hit;
   
   @Column(insertable = true,updatable = false)
   @ColumnDefault("SYSDATE")
   private String regdate;
   
   @PrePersist // 날짜 변환 
   public void regdate() {
	   this.regdate=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
   }
}