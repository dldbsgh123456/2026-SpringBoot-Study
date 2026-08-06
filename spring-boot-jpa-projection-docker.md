# Spring Boot - Projection / Native Query / 레이아웃 구성 / Docker

**▶ JPA Interface Projection - Entity 대신 필요한 컬럼만 받기**

```java
public interface FoodVO {
    public int getNo();
    public String getName();
    public String getAddress();
    public String getPoster();
}
```

```java
@Query(value="SELECT no,name,poster,address "
      +"FROM food "
      +"ORDER BY no ASC "
      +"OFFSET :start ROWS FETCH NEXT 12 ROWS ONLY", nativeQuery=true)
public List<FoodVO> foodListData(@Param("start") int start);
```

- `@Entity`(`FoodEntity`)는 테이블 전체 컬럼을 다 갖고 있는 클래스인 반면, `FoodVO`는 getter만 있는 **인터페이스**로 선언
- 목록 화면처럼 일부 컬럼만 필요할 때, Repository가 이 인터페이스 타입으로 결과를 반환하면 Spring Data JPA가 필요한 필드만 채워서 구현체를 자동 생성해줌 (Interface-based Projection)
- Entity 전체를 조회하지 않아도 되니 목록처럼 가벼운 데이터가 필요한 조회에 적합

**▶ `nativeQuery=true` - 메소드 이름 규칙으로 안 되는 쿼리는 SQL 직접 작성**

- `OFFSET :start ROWS FETCH NEXT 12 ROWS ONLY`처럼 페이징 SQL은 메소드 이름 규칙(`findBy...`)으로 표현이 안 되기 때문에 `@Query(nativeQuery=true)`로 순수 SQL을 그대로 작성
- `:start`는 `@Param("start")`로 지정한 파라미터와 매칭 (MyBatis의 `#{start}`와 비슷한 역할)
- 정리하면 조회 방식이 3단계로 늘어난 것: ① 메소드 이름 규칙(`findByNo`) ② JPQL/`Pageable` ③ 그래도 안 되면 `@Query(nativeQuery=true)`로 SQL 직접 작성

---

**▶ Thymeleaf 레이아웃 구성 - 헤더 + 본문 include**

```html
<!-- main.html : 모든 요청이 공통으로 거치는 뼈대 -->
<body>
  <th:block th:include="main/header"></th:block>
  <th:block th:include="${main_html}"></th:block>
</body>
```

```java
model.addAttribute("main_html", "main/home");   // 음식 목록
// 또는
model.addAttribute("main_html", "main/goods");  // 상품 목록
return "main/main";   // 항상 같은 뼈대(main.html)로 이동
```

- 이전 JSP 버전에서 `main_jsp` 속성 + `<jsp:include>`로 화면 본문만 갈아 끼우던 것과 같은 개념을 Thymeleaf로 옮긴 것
- `th:include="${main_html}"`처럼 **경로 자체를 변수로 동적 지정**할 수 있어서, 컨트롤러가 어떤 화면을 보여줄지 문자열 값으로 결정
- 헤더(`main/header`)는 고정, 본문만 컨트롤러가 넘겨준 값에 따라 바뀌는 구조

---

**▶ 스프링 주요 어노테이션 한눈에 정리**

| 어노테이션 | 역할 |
|---|---|
| `@Controller` | 화면 변경 + 데이터 전송 (Router) |
| `@RestController` | 데이터만 전송 (JSON) → JS/Vue 연동용 |
| `@Aspect` | 공통 모듈(AOP) 정의 |
| `@Autowired` | 자동 주입 (필드 방식, 비권장) |
| `@RequiredArgsConstructor` | 생성자 주입 자동 생성 (Lombok) |
| `@RequestParam` | 단일 요청값 하나 받기 |
| `@ModelAttribute` | 커맨드 객체(VO) 단위로 받기 |
| `@RequestBody` | JSON → 객체 변환 |
| `@ResponseBody` | `@Controller`에서 JS로 데이터 전송 (`@RestController`가 이걸 기본 포함) |
| `@Repository` | DB 연동 저장소 |
| `@Component` | 일반 객체 메모리 할당 (Security/WebSocket/AOP 등) |
| `@Service` | 여러 DAO/Repository를 묶어 기능 처리 |
| `@ControllerAdvice` | 전역 예외처리 |

- **SpringFramework**: XML 설정 + 어노테이션 혼용
- **SpringBoot**: XML 없이 어노테이션만으로 설정 (자동 구성)

---

**▶ Dockerfile - Spring Boot jar 컨테이너로 배포**

```dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

- `FROM eclipse-temurin:21-jdk`: JDK 21이 설치된 베이스 이미지 사용
- `COPY build/libs/*-SNAPSHOT.jar app.jar`: Gradle 빌드 결과물(jar)을 이미지 안에 `app.jar`로 복사
- `EXPOSE 8080`: 컨테이너가 8080 포트를 사용한다고 명시(문서화 목적, 실제 포트 매핑은 `docker run -p`에서 결정)
- `ENTRYPOINT ["java","-jar","app.jar"]`: 컨테이너 시작 시 실행할 명령
- 이전에 봤던 "jar/war 파일이 크면 CI/CD 속도가 느리다"는 내용과 이어지는 부분 — SpringBoot는 내장 톰캣을 포함한 jar 하나로 빌드되기 때문에, 이 Dockerfile처럼 jar 하나만 복사해서 바로 실행 가능한 구조가 됨
