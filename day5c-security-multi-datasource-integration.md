# 5일차 C - Security를 멀티 DataSource 프로젝트에 연동

**▶ 이미 만들어둔 `oracleDataSource` Bean을 Security가 그대로 재사용**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final DataSource dataSource;

    public SecurityConfig(
            LoginSuccessHandler loginSuccessHandler,
            LoginFailureHandler loginFailureHandler,
            @Qualifier("oracleDataSource") DataSource dataSource) {   // 1일차에 만든 그 Bean
        this.dataSource = dataSource;
        ...
    }

    @Bean
    public JdbcUserDetailsManager jdbcUserDetailsManager() {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        manager.setUsersByUsernameQuery(
                "SELECT username,password,enabled FROM member WHERE username=?");
        manager.setAuthoritiesByUsernameQuery(
                "SELECT username,a.member_id,authority FROM authority a "
              + "JOIN member m ON a.member_id=m.member_id AND username=?");
        return manager;
    }
}
```

- 1일차(다중 데이터소스 설정)에서 `@Bean(name="oracleDataSource")`로 등록해둔 `DataSource`를, 이번엔 **회원 인증용 DB 연결로 재사용** — 레시피 데이터도 Oracle, 회원 정보도 Oracle이라 같은 커넥션 풀을 그대로 활용
- `@Qualifier("oracleDataSource")`: `DataSource` Bean이 (Oracle/PostgreSQL) 두 개라서, 생성자에서 정확히 어떤 걸 주입받을지 이름으로 지정해야 함 — 1일차에서 겪었던 것과 완전히 같은 이유

**▶ `JdbcUserDetailsManager`에 JOIN이 들어간 권한 조회 쿼리**

```sql
SELECT username, a.member_id, authority
FROM authority a
JOIN member m ON a.member_id = m.member_id
AND username = ?
```

- 회원(`member`) 테이블과 권한(`authority`) 테이블이 `member_id`로 연결되어 있어서, 로그인한 사용자의 권한 목록을 가져올 때 **두 테이블을 JOIN**해서 조회 — 이전에 다룬 JdbcUserDetailsManager 예시(단일 테이블 조회)보다 한 단계 더 실제 서비스에 가까운 스키마 구조

---

**▶ Security 구성요소 전체 용어 정리 (코드 주석 기반)**

| 구성요소 | 역할 |
|---|---|
| `@EnableWebSecurity` | Spring Security를 AOP 방식으로 활성화 |
| `SecurityConfig` | 보안 전체 설정을 담당하는 사용자 정의 클래스 |
| `HttpSecurity` | 접근권한 / 로그인 / 로그아웃 / 자동로그인(rememberMe) 등을 설정하는 빌더 |
| `SecurityFilterChain` | HTTP 요청에 대한 Security 필터 처리 순서를 정의한 결과물 |
| `AuthenticationManager` | 사용자 인증 수행을 총괄하는 관리자 |
| `AuthenticationProvider` | 실제 인증 로직을 수행하는 객체 |
| `UserDetailsService` | 로그인한 사용자의 정보를 조회하는 인터페이스 |
| `JdbcUserDetailsManager` | DB에서 SQL로 사용자/권한을 조회해서 `UserDetailsService`를 구현해주는 구현체 |
| `UserDetails` | 한 명의 사용자 정보가 담긴 객체 |
| `BCryptPasswordEncoder` | 비밀번호를 암호화(해시)하는 인코더 |
| `JdbcTokenRepositoryImpl` | 자동 로그인을 위해 사용자별 토큰을 DB에 저장하는 저장소 |
| `SecurityContext` | 인증 정보를 담아두는 보관소 |
| `rememberMe` | 자동 로그인 기능 전체를 설정하는 블록 |

**▶ DB 테이블 구조 - 회원/권한 분리**

```
member (회원 정보)          authority (권한 정보)
─────────────────         ─────────────────
member_id (PK)      ───┐   member_id (FK)
username                └──▶ authority (예: ROLE_USER, ROLE_ADMIN, ROLE_INSTRUCTOR)
password
enabled
```

- 회원 한 명이 여러 권한을 가질 수 있도록 **권한을 별도 테이블로 분리**(1:N 구조) — 이전 Security 파일들에서 다룬 것과 같은 설계 패턴을 이번엔 실제 JOIN 쿼리로 구현
