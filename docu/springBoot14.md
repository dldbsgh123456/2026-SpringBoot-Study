**▶ RESTful - HTTP 메소드와 CRUD 매핑**

```
GET    → Select (조회)
POST   → Insert (등록)
PUT    → Update (수정)
DELETE → Delete (삭제)
```

- 지금까지는 조회(`GET`)만 계속 다뤘는데, 등록/수정/삭제까지 가면 **HTTP 메소드 자체가 어떤 동작인지**를 나타내도록 맞추는 게 RESTful 설계의 기본 규칙
- Vue/React 같은 클라이언트에서 `@RequestBody`로 JSON을 받아 VO로 변환하는 구조:
  ```
  { name:'', subject:'', content:'', pwd:'' }  ==(JSON)==>  @RequestBody BoardVO vo
  ```

**▶ 클라이언트 / 서버 조합 예시**

| 클라이언트 | 서버(선택지) |
|---|---|
| Vue | SpringFramework / Spring-Boot |
| React | NodeJS / Django / FastAPI / ASP.NET |

- REST API 방식이면 클라이언트와 서버가 **어떤 언어/프레임워크 조합이든 JSON으로만 통신**하면 되므로 자유롭게 조합 가능하다는 걸 보여주는 예시表

---

**▶ MyBatis - XML과 어노테이션 사용 기준**

```
XML = SQL문장이 복잡한 경우에 사용 (JOIN / 동적쿼리)
단순한 SQL = 어노테이션(@Select, @Insert, @Update) 이용
```

- 지금까지 프로젝트에서 단순 목록/상세/조회수 증가 같은 건 전부 `@Select`, `@Update` 어노테이션으로 처리하고, `INTERSECT`가 들어가는 것처럼 조건이 복잡해지는 경우만 XML(`<sql>`, `<include>`)로 뺐던 이유가 이 기준 때문
- 하나의 Mapper 인터페이스 안에서도 간단한 쿼리는 어노테이션, 복잡한 쿼리는 XML로 **섞어 써도 무방**함

---

**▶ 스프링 어노테이션 - `@Configuration`이 담당하는 영역**

| 어노테이션 | 역할 |
|---|---|
| `@Repository` | 데이터베이스 연동(저장소) 구분 |
| `@Service` | 요청 처리(BI, 비즈니스 로직) |
| `@Controller` | 화면 변경 |
| `@RestController` | JS(axios/fetch)로 JSON/문자열 결과 전송 |
| `@Component` | AOP / Manager 등 일반 클래스 등록 |
| `@ControllerAdvice` / `@RestControllerAdvice` | 예외처리 |
| `@Configuration` | 자바 환경설정 — **Spring Security(JWT) 설정, WebSocket 설정, QueryDSL 설정** 등을 여기서 Bean으로 등록 |

- 이전에 `@Configuration`으로 `JPAQueryFactory` Bean을 등록했던 것도 이 범주 — 앞으로 나올 JWT 인증 설정, WebSocket 설정도 같은 방식(`@Configuration` + `@Bean`)으로 등록하게 됨
