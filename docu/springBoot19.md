# Spring Security - 로그인 / 권한(인가) 처리

**▶ 전체 구조**

| 클래스 | 역할 |
|---|---|
| `SecurityConfig` | URL별 접근 권한, 로그인/로그아웃 설정 (필터체인 정의) |
| `CustomUserDetailsService` | DB에서 회원 조회 → Security가 이해하는 `UserDetails`로 변환 |
| `UserMapper` | 아이디/비밀번호, 권한 목록 조회 (MyBatis) |
| `LoginFailHandler` | 로그인 실패 시 원인별 에러 메시지 처리 |
| `MemberVO` / `AuthorityVO` | 회원 정보 / 권한 정보 VO |

---

**▶ `SecurityFilterChain` - URL별 권한 + 로그인/로그아웃 설정**

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/","/join","/login").permitAll()   // 누구나 접근
                .requestMatchers("/user").authenticated()             // 로그인만 하면 접근
                .requestMatchers("/admin").hasRole("ADMIN")            // ADMIN 권한만
                .anyRequest().permitAll()                              // 나머지는 게스트도 허용
            )
            .formLogin(form -> form
                .loginPage("/login")                    // 커스텀 로그인 화면 사용
                .loginProcessingUrl("/login_process")    // 로그인 폼이 실제 전송할 주소
                .defaultSuccessUrl("/", true)            // 성공 시 이동할 주소
                .failureHandler(loginFailHandler())      // 실패 시 처리 위임
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // 비밀번호 암호화
    }

    @Bean
    public AuthenticationFailureHandler loginFailHandler() {
        return new LoginFailHandler();
    }
}
```

- `csrf().disable()`: CSRF(다른 사이트에서 인증된 브라우저의 쿠키/세션을 도용해 위조 요청 보내는 공격) 방지 기능을 끔 — 폼 기반 인증에서 별도 CSRF 토큰 처리를 안 하는 경우 임시로 꺼두는 설정
- `requestMatchers(경로).권한()` 순서가 중요 — 위에서부터 매칭되므로, 좁은 조건(구체적인 URL)을 먼저 쓰고 넓은 조건(`anyRequest()`)을 맨 아래에 둠
- `permitAll()`(누구나) / `authenticated()`(로그인 필요) / `hasRole("ROLE명")`(특정 권한만) / `hasAnyRole(...)`(여러 권한 중 하나) 로 접근 범위 지정
- `formLogin`에서 `loginPage`를 지정하지 않으면 Security가 기본 제공하는 로그인 화면이 뜨고, 지정하면 직접 만든 `login.html`을 사용

---

**▶ `UserDetailsService` - DB 회원정보를 Security가 이해하는 형태로 변환**

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserMapper mapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        MemberVO user = mapper.findByUserid(username);
        if (user == null) {
            throw new UsernameNotFoundException("UserName을 찾을 수 없습니다");
        }
        List<String> roles = mapper.findRolesByUserid(username);
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        return new User(
            user.getUsername(), user.getUserpwd(),
            user.getEnable() == 0 ? false : true,   // 계정 활성화 여부
            true, true, true,                        // 계정만료/잠금/비번만료 여부
            authorities
        );
    }
}
```

- Security는 로그인 시도 시 `loadUserByUsername(입력한아이디)`를 자동으로 호출 — 이 메소드 안에서 DB 조회 후 `UserDetails`(Security 전용 사용자 객체)로 감싸서 반환하기만 하면 나머지 인증 로직(비밀번호 대조, 세션 생성 등)은 Security가 알아서 처리
- 권한은 문자열 그대로가 아니라 `SimpleGrantedAuthority`로 감싸야 함 (DB에는 `ROLE_ADMIN`, `ROLE_USER`처럼 저장)
- `User(...)` 생성자의 boolean 4개는 순서대로 **계정 활성화 / 계정 만료 안됨 / 계정 잠김 안됨 / 비밀번호 만료 안됨** — `enable` 컬럼(휴먼계정 여부)만 DB값을 반영하고 나머지는 이 프로젝트에서는 항상 `true`로 고정

---

**▶ 로그인 실패 시 원인별 메시지 - `AuthenticationFailureHandler`**

```java
public class LoginFailHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String errMsg = "아이디나 비밀번호가 틀립니다!!";
        if (exception instanceof DisabledException) {
            errMsg = "휴먼 계정입니다";
        } else if (exception instanceof LockedException) {
            errMsg = "잠긴 계정입니다";
        }
        request.getSession().setAttribute("loginError", errMsg);
        response.sendRedirect("/login?error");
    }
}
```

- Security가 던지는 예외 타입(`DisabledException`, `LockedException` 등)을 `instanceof`로 구분해서 **상황별로 다른 에러 메시지**를 세션에 담아 로그인 페이지로 리다이렉트
- `loadUserByUsername`에서 반환한 `UserDetails`의 활성화/잠금 값에 따라 Security가 자동으로 이 예외들을 판단해서 던져줌

---

**▶ 컨트롤러에서 로그인한 사용자 정보 꺼내기 (`@AuthenticationPrincipal`)**

```java
@GetMapping("/user")
public String user(@AuthenticationPrincipal UserDetails userDetails, Model model) {
    model.addAttribute("id", userDetails.getUsername());
    model.addAttribute("roles", userDetails.getAuthorities());
    return "mypage";
}
```

- `@AuthenticationPrincipal`을 매개변수에 붙이면, 현재 로그인된 사용자의 `UserDetails`(위 `CustomUserDetailsService`가 만든 그 객체)를 별도 조회 없이 바로 받을 수 있음

---

**▶ Thymeleaf에서 로그인 상태/권한별로 화면 분기 (`sec:` 네임스페이스)**

```html
<html xmlns:sec="http://www.thymeleaf.org/extras/spring-security">
...
<tr sec:authorize="isAnonymous()">      <!-- 비로그인 상태에만 노출 -->
  <td><a href="/login">로그인</a></td>
</tr>
<tr sec:authorize="isAuthenticated()">  <!-- 로그인 상태에만 노출 -->
  <strong sec:authentication="name"></strong>&nbsp;환영합니다
</tr>
<tr sec:authorize="hasRole('ADMIN')">   <!-- ADMIN 권한만 노출 -->
  <a href="/admin">관리자페이지</a>
</tr>
```

- `sec:authorize="조건식"`: 조건을 만족할 때만 해당 태그를 화면에 렌더링 (`isAnonymous()`, `isAuthenticated()`, `hasRole('...')` 등)
- `sec:authentication="name"` / `sec:authentication="principal.authorities"`: 로그인한 사용자의 아이디/권한 정보를 컨트롤러에서 안 넘겨도 화면에서 직접 출력 가능
- 이 네임스페이스를 쓰려면 Thymeleaf Security 확장 의존성(`thymeleaf-extras-springsecurity`)이 필요
