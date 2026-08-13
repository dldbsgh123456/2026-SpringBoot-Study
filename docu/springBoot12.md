# Spring Boot / JPA - 조회 방식 3가지 (메소드 규칙 / JPQL / QueryDSL)

**▶ 연관관계 매핑 (`@ManyToOne`)**

```java
@Entity
@Table(name="EMP")
@Getter @Setter
public class Emp {
    @Id
    private int empno;
    private String ename, job;
    private Integer mgr, comm;   // null 허용 컬럼은 int가 아니라 Integer로 선언
    private Date hiredate;
    private int sal;

    @ManyToOne
    @JoinColumn(name="deptno")
    private Dept dept;           // Emp 여러 개가 Dept 하나를 참조
}
```

```java
@Entity
@Table(name="DEPT")
@Getter @Setter
public class Dept {
    @Id
    private int deptno;
    private String dname, loc;
}
```

- `comm`, `mgr`처럼 컬럼값이 `NULL`일 수 있으면 기본 타입(`int`) 대신 **래퍼 타입(`Integer`)**으로 선언해야 함 (`int`는 null을 담을 수 없음)
- `@ManyToOne` + `@JoinColumn(name="deptno")`로 FK 관계를 객체 참조(`emp.getDept().getDname()`)로 다룰 수 있음 — SQL의 JOIN을 엔티티 그래프 탐색으로 대체

---

**▶ 1. 메소드 규칙 (Method Query) - SQL 없이 이름으로 자동 생성**

```java
public interface EmpMethodRepository extends JpaRepository<Emp, Integer> {
    Emp findByEmpno(int empno);                              // WHERE empno=?
    List<Emp> findByEnameContains(String ename);              // WHERE ename LIKE '%?%'
    List<Emp> findBySalGreaterThanEqual(int sal);              // WHERE sal>=?
    List<Emp> findBySalBetween(int min, int max);              // WHERE sal BETWEEN ? AND ?
    List<Emp> findByJobAndSalGreaterThan(String job, int sal);  // AND 조건
    List<Emp> findByDeptDnameContains(String dname);            // 연관 엔티티(Dept)까지 파고들어 조건
    List<Emp> findByOrderBySalDesc();                            // 정렬
    List<Emp> findTop3ByOrderBySalDesc();                        // Top-N
    List<Emp> findDistinctByJob(String job);                     // 중복 제거
    List<Emp> findByCommIsNull();                                 // NULL 체크
    List<Emp> findByDeptDeptnoIn(List<Integer> deptnos);          // IN
    List<Emp> findByJobNot(String job);                           // NOT
}
```

- `findByDeptDnameContains`처럼 **연관 엔티티의 필드까지 이름에 이어 붙이면** 자동으로 JOIN까지 만들어줌 (`emp.dept.dname`)
- 이름만 보고 어떤 조건인지 바로 파악 가능 → 가독성은 가장 좋음

---

**▶ 2. JPQL (`@Query`) - 엔티티 객체 기준의 SQL**

```java
public interface EmpJpqlRepository extends JpaRepository<Emp, Integer> {

    @Query("SELECT e FROM Emp e")                    // Emp는 테이블명이 아니라 엔티티명, 별칭(e) 필수
    List<Emp> empListData();

    @Query("SELECT e FROM Emp e WHERE e.empno=:empno")
    Emp empDetailData(@Param("empno") int empno);

    @Query("SELECT e FROM Emp e WHERE e.ename LIKE CONCAT('%',:ename,'%')")
    List<Emp> empLikeData(@Param("ename") String ename);

    @Query("SELECT e FROM Emp e JOIN e.dept d WHERE d.dname=:dname")
    List<Emp> findByDeptDname(@Param("dname") String dname);

    @Query("SELECT e FROM Emp e WHERE e.dept.deptno IN :deptnos")
    List<Emp> findByDeptDeptnoIn(@Param("deptnos") List<Integer> deptnos);
}
```

- `FROM Emp e`는 테이블이 아니라 **엔티티 클래스**를 대상으로 하고, 반드시 별칭(`e`)을 지정해야 함
- `:파라미터명` + `@Param("파라미터명")`으로 바인딩 (MyBatis의 `#{}`와 비슷한 위치)
- `LIKE '%'||:ename||'%'` 대신 `LIKE CONCAT('%',:ename,'%')`처럼 DB 종속적이지 않은 표준 함수(`CONCAT`)를 사용

---

**▶ 3. QueryDSL - 타입 안전한 코드로 쿼리 조립**

```java
// 1) Bean 등록 - EntityManager로 JPAQueryFactory 생성
@Configuration
public class QueryFactoryConfig {
    @PersistenceContext
    private EntityManager em;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(em);
    }
}
```

```java
@Repository
@RequiredArgsConstructor
public class EmpQueryRepository {
    private final JPAQueryFactory queryFactory;

    public Emp findByEmpno(int empno) {
        QEmp emp = QEmp.emp;   // 빌드 시 자동 생성되는 Q-class
        return queryFactory.from(emp)
                .where(emp.empno.eq(empno))
                .fetchOne();
    }

    public List<Emp> findByDeptDname(String dname) {
        QEmp emp = QEmp.emp;
        QDept dept = QDept.dept;
        return queryFactory.from(emp)
                .join(emp.dept, dept)
                .where(dept.dname.eq(dname))
                .fetch();
    }

    public List<Emp> findByTop3Sal() {
        QEmp emp = QEmp.emp;
        return queryFactory.from(emp)
                .orderBy(emp.sal.desc())
                .limit(3)
                .fetch();
    }

    public List<Integer> findDistinctSal() {
        QEmp emp = QEmp.emp;
        return queryFactory.select(emp.sal)
                .distinct()
                .from(emp)
                .fetch();
    }
}
```

- 엔티티(`Emp`)를 빌드하면 필드 이름 그대로 만들어진 **Q-class(`QEmp`)**가 자동 생성됨 — 이걸 이용해서 문자열이 아닌 **코드(메소드 체이닝)**로 조건을 조립
- 연산자도 메소드로 표현: `eq`(=), `ne`(!=), `gt`/`lt`(초과/미만), `goe`/`loe`(이상/이하), `between`, `in`, `contains`/`startsWith`/`endsWith`, `isNull`/`isNotNull`
- `.where(a, b)`처럼 콤마로 여러 조건을 넘기면 AND로 묶이고, `.and()`/`.or()`로 명시적으로 연결도 가능
- 오타가 나면 **컴파일 에러**로 바로 잡히는 게 JPQL(문자열이라 런타임에야 에러 발견)과의 가장 큰 차이

---

**▶ 세 가지 방식 비교**

| 구분 | 메소드 규칙 | JPQL (`@Query`) | QueryDSL |
|---|---|---|---|
| SQL 지식 필요 여부 | 거의 불필요 | 필요 (JPQL 문법) | 필요 없음 (코드로 조립) |
| 가독성 | 메소드명만 봐도 조건 파악 가능 | 문자열이라 조건이 길면 읽기 어려움 | 메소드 체이닝이라 구조 파악 쉬움 |
| 오타/오류 발견 시점 | 컴파일 시점 (메소드명 자체가 규칙) | **런타임** (문자열이라 오타 나도 컴파일은 통과) | **컴파일 시점** (Q-class 기반이라 타입 체크됨) |
| 동적 쿼리 | 불가능 (조건이 고정) | 어려움 | 가능 (조건을 코드로 조립하니 if문 등으로 유연하게 구성) |
| 초기 설정 | 없음 (인터페이스 상속만) | 없음 | Q-class 생성 설정 필요, `JPAQueryFactory` Bean 등록 필요 |
| 메소드명 길이 | 조건 많아지면 매우 길어짐 (`findBySalGreaterThanAndEnameLikeAndJobLikeOrderByHiredateDesc`) | 상대적으로 짧음 | 짧음 |
| 주 사용처 | 단순 조회, 조건 몇 개짜리 기본 검색 | 복잡하지 않은 조회, UPDATE/DELETE | 복잡한 JOIN/필터링/동적 조건이 필요한 검색, 페이징 |
| 실무 체감 사용 비율 | 8 | (JPQL+QueryDSL 합쳐서) 2 | ↑ |

**정리:**
- 단순 조회(단일 컬럼 조건, 기본 CRUD) → **메소드 규칙**이 제일 빠르고 간단
- 복잡하지 않은 조회나 UPDATE/DELETE처럼 정적인 쿼리 → **JPQL**
- 조건이 여러 개 겹치는 동적 검색, 복잡한 JOIN/페이징이 필요한 경우 → **QueryDSL** (설정은 번거롭지만 타입 안전성과 동적 쿼리 조립이 강점)
