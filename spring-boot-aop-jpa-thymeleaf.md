# Spring Boot - AOP / JPA / Thymeleaf

**▶ AOP로 공통 로그 처리 (`@Aspect` + `@Around`)**

```java
@Aspect        // 공통으로 적용되는 기능
@Component     // 일반 클래스 => 메모리 할당 요청
public class FoodAOP {
   @Around("execution(* com.sist.web.controller.*Controller.*(..))")
   public Object log(ProceedingJoinPoint jp) throws Throwable {
       Object obj = null;
       System.out.println("메소드 호출 : " + jp.getSignature().getName());
       long start = System.currentTimeMillis();
       obj = jp.proceed();                 // 실제 대상 메소드 실행
       long end = System.currentTimeMillis();
       System.out.println("메소드 종료 : " + (end - start) + "MS");
       return obj;
   }
}
```

- `@Transactional`은 스프링이 이미 만들어둔 AOP를 어노테이션으로 "가져다 쓰는" 것이었다면, 이건 개발자가 **직접 AOP를 정의**하는 방식
- `execution(* 패키지.클래스.메소드(매개변수))` 형식으로 "어떤 메소드에 적용할지"를 지정 — `*Controller.*(..)`는 이름이 Controller로 끝나는 모든 클래스의 모든 메소드
- `@Around`는 메소드 호출 전/후를 모두 감쌀 수 있어서 실행 시간 측정 같은 로깅에 적합 (`@Before`/`@After`/`@AfterThrowing`/`@AfterReturning`은 각각 진입 전, finally, catch, 정상 반환 시점에만 개입)
- 트랜잭션, 로그 파일 기록, 공통 데이터(예: 푸터에 들어가는 데이터) 출력 등 "여러 컨트롤러에 공통으로 걸고 싶은 기능"에 쓰는 패턴

---

**▶ JPA `Repository` - SQL 없이 메소드 이름으로 쿼리 생성**

```java
@Entity
@Table(name="food")
@Data
public class FoodEntity {
    @Id
    private int no;
    private int likecount, jjimcount, hit, replycount;
    private String name, address, phone, ...;
}
```

```java
@Repository
public interface FoodRepository extends JpaRepository<FoodEntity, Integer> {
    public FoodEntity findByNo(int no);   // SELECT * FROM food WHERE no=?
}
```

- MyBatis처럼 SQL을 직접 안 써도, `JpaRepository<엔티티타입, PK타입>`을 상속하면 `findAll`, `count`, `save` 같은 기본 CRUD 메소드가 자동으로 생김
- 메소드 이름 규칙만으로 조건절이 만들어짐:
  - `findByAddressContains(String address)` → `LIKE '%address%'`
  - `findByAddressStartsWith`/`findByAddressEndsWith` → `LIKE 'address%'` / `LIKE '%address'`
  - `findBy컬럼명Between`, `findByDistinct컬럼명`, `findByOrderBy컬럼명Desc` 등도 같은 방식
- 조회 → 값 수정 → `save()` 흐름으로 UPDATE 처리:
  ```java
  FoodEntity vo = fr.findByNo(no);
  vo.setHit(vo.getHit() + 1);
  fr.save(vo);   // PK가 이미 존재하면 UPDATE로 동작
  ```

---

**▶ JPA `Pageable`로 페이징 처리 (MyBatis의 OFFSET/FETCH 대체)**

```java
final int ROWSIZE = 12;
Pageable pg = PageRequest.of(page - 1, ROWSIZE, Sort.by(Sort.Direction.ASC, "no"));
Page<FoodEntity> pList = fr.findAll(pg);

List<FoodEntity> list = new ArrayList<>();
if (pList != null && pList.hasContent()) {
    list = pList.getContent();   // Page 객체 => List로 변환
}
```

- `PageRequest.of(페이지인덱스(0부터), 페이지크기, 정렬조건)`으로 페이징 조건을 객체 하나로 표현
- 직접 `OFFSET ... FETCH NEXT ...` SQL을 안 써도 `findAll(Pageable)`이 알아서 페이징 쿼리를 만들어줌
- 블록(시작페이지/끝페이지) 계산 로직 자체는 기존 MyBatis 버전과 동일 — 차이는 실제 데이터 조회 방식(SQL 문자열 vs `Pageable` 객체)

---

**▶ Thymeleaf 문법 (JSP/JSTL 대체 템플릿 엔진)**

| 표현식 | 의미 |
|---|---|
| `${}` | Model로 전달된 데이터 참조 (EL과 유사) |
| `*{}` | Form 객체 참조 (사용 빈도 낮음) |
| `#{}` | properties 값 참조 |
| `@{}` | URL 생성 |
| `~{}` | Fragment(레이아웃) include |

```html
<!-- 값 출력 -->
<h3>[[${vo.name}]]</h3>
<span th:text="${vo.name}"></span>

<!-- URL + 쿼리파라미터 자동 생성 -->
<a th:href="@{/food/detail(no=${vo.no})}">...</a>
<!-- => /food/detail?no=1 로 변환됨 -->

<!-- 반복문 -->
<div th:each="vo:${list}">...</div>

<!-- 조건문 -->
<li th:if="${startPage>1}">...</li>
<li th:class="${curpage==i?'active':''}">...</li>

<!-- 페이지 번호 시퀀스 -->
<li th:each="i:${#numbers.sequence(startPage,endPage)}">[[${i}]]</li>
```

- `th:utext`는 HTML 태그까지 그대로 출력(예: `<b>`가 굵은 글씨로 렌더링)하지만, 사용자 입력값에 쓰면 XSS 위험이 있어 가급적 `th:text`만 사용 권장
- JSP는 `.jsp` 파일 자체가 서버에서 서블릿으로 컴파일되는 방식이라 war 배포가 무거운 반면, Thymeleaf는 순수 HTML에 속성만 붙이는 방식이라 스프링부트의 jar 배포와 궁합이 맞음

---

**▶ REST API에서 `ResponseEntity`로 상태코드까지 제어**

```java
@GetMapping("/goods/list_vue")
public ResponseEntity<Map> goods_list(@RequestParam("page") int page) {
    Map map = new HashMap<>();
    try {
        List<GoodsEntity> list = gService.goodsListData(page);
        int[] datas = gService.goodsPageData(page);
        map.put("list", list);
        map.put("curpage", datas[0]);
        ...
    } catch (Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    return ResponseEntity.ok(map);   // 200 + body
}
```

- 이전에 다룬 `Map` 그냥 반환하는 방식은 항상 200으로 응답되지만, `ResponseEntity`를 쓰면 실패 시 500 같은 상태코드를 명시적으로 내려줄 수 있음
- Vue 쪽에서 axios로 호출 시 상태코드를 보고 에러 처리를 분기할 수 있게 됨
