package com.sist.web.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;
import com.sist.web.entity.*;
import com.sist.web.vo.*;
// 단점 : JOIN이 어렵다 / SubQuery를 지원하지 않는다
// MyBatis 8:2 JPA
@Repository
public interface BoardRepository extends JpaRepository<BoardEntity,Integer>{
    public BoardEntity findByNo(int no); // 상세보기
    // 직접 SQL 문장 생성해도 됨 => findAll(Page)
    @Query(value="SELECT no,subject,name,hit,TO_CHAR(regdate,'yyyy-MM-dd') as dbday FROM jpaboard ORDER BY no DESC "
                +"OFFSET :start ROWS FETCH NEXT 10 ROWS ONLY",
                nativeQuery = true) // SQL을 JPQL로 변경없이 문장 그대로 
    public List<BoardDTO> boardListData(@Param("start") Integer start);
    /*
     *   findBy
     *   find SELECT *
     *     By WHERE no=1 (int no)
     *   
     *   findByNameLike
     *        WHERE name Like
     *        
     *   findByNoBetweenAnd(int a,int b)
     *        WHERE name Like
     *        
     *   findByNameStartsWith A%
     *        WHERE name Like
     *   
     *   findByNameEndsWith %A
     *        WHERE name Like
     *        
     *   findByNameContainsh %A%
     *       WHERE name Like
     *   
     */
    // save(Update,insert) , delete
}
