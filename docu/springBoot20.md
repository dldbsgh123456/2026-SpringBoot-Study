# Spring Security 심화 - JDBC 인증 / 자동 로그인(remember-me) / 핸들러

**▶ 커스텀 `UserDetailsService` 대신 `JdbcUserDetailsManager`로 SQL만 지정**

```java
@Bean
public JdbcUserDetailsManager jdbcUserDetailsService() {
    JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
    manager.setUsersByUsernameQuery(
        "SELECT userid as username,userpwd as password,enable "
      + "FROM springmember WHERE userid=?"
    );
    manager.setAuthoritiesByUsernameQuery(
        "SELECT userid as username, authority "
      + "FROM authority WHERE userid=?"
    );
    return manager;
}
```

- 이전엔 `implements UserDetailsService`로 직접 클래스를 만들어서 Mapper 호출 → `User(...)` 조립까지 코드로 다 작성했는데, 여기서는 **Spring Security가 기본 제공하는 `JdbcUserDetailsManager`**에 조회 SQL 두 개(회원 정보용, 권한 정보용)만 문자열로 지정
- 조건: SELECT 결과 컬럼명이 각각 `username`, `password`(+`enable`), `username`+`authority`로 나와야 함 — Security가 정해놓은 컬럼명 규칙에 맞춰 `AS`로 별칭을 줌
- 커스텀 로직(조회수 증가, 별도 가공 등)이 필요 없는 단순한 인증이면 이 방식이 클래스 하나 안 만들어도 돼서 더 간단함

**▶ `AuthenticationManager`를 직접 Bean으로 구성**

```java
@Bean
public AuthenticationManager authenticationManager(
        HttpSecurity http, BCryptPasswordEncoder passwordEncoder) throws Exception {
    AuthenticationManagerBuilder builder =
            http.getSharedObject(AuthenticationManagerBuilder.class);
    builder
        .userDetailsService(jdbcUserDetailsService())
        .passwordEncoder(passwordEncoder());
    return builder.build();
}
```

- `AuthenticationManagerBuilder`에 "사용자 조회는 이 `UserDetailsService`로, 비밀번호 비교는 이 인코더로 해라"를 등록해서 `AuthenticationManager`를 조립
- 이 매니저가 실제로 아이디/비밀번호를 검증하는 주체 — `formLogin()` 설정은 "언제/어떤 URL로 로그인 시도를 받을지"만 정의하고, 실제 검증은 이 매니저가 담당

---

**▶ 로그인 폼 파라미터명 커스터마이징**

```java
.formLogin(form -> form
    .loginPage("/member/login")
    .loginProcessingUrl("/member/login_process")
    .usernameParameter("userid")     // 기본값은 "username"
    .passwordParameter("userpwd")    // 기본값은 "password"
    .defaultSuccessUrl("/", false)
    .successHandler(loginSuccessHandler)
    .failureHandler(loginFailHandler)
    .permitAll()
)
```

```html
<input type=text name=userid>
<input type=password name=userpwd>
```

- Security의 `UsernamePasswordAuthenticationFilter`는 기본적으로 `username`/`password`라는 파라미터명을 찾는데, `usernameParameter`/`passwordParameter`로 실제 폼의 `name` 속성에 맞춰 바꿀 수 있음
- `defaultSuccessUrl("/", false)`의 두 번째 인자가 `false`면, 로그인 전에 원래 가려던 페이지가 있었으면 로그인 후 **그 페이지로 돌려보냄** (`true`면 무조건 지정한 URL로만 이동)

---

**▶ 자동 로그인 (`rememberMe`) - DB에 토큰 저장**

```java
.rememberMe(remember -> remember
    .key("my-secret-key")
    .rememberMeParameter("remember-me")     // 체크박스 name
    .tokenValiditySeconds(60 * 60 * 24)     // 유지 기간: 1일
    .tokenRepository(persistentTokenRepository())
)
```

```java
@Bean
public PersistentTokenRepository persistentTokenRepository() {
    JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
    repo.setDataSource(dataSource);
    return repo;   // persistent_logins 테이블에 토큰 저장
}
```

```html
<input type="checkbox" name="remember-me">자동 로그인
```

- 체크박스(`remember-me`)를 체크하고 로그인하면, 세션이 끊겨도(브라우저 재시작 등) 자동으로 로그인 상태를 복원할 수 있도록 **DB(`persistent_logins` 테이블)에 토큰을 저장**
- 로그아웃 시 `.deleteCookies("remember-me","JSESSIONID")`로 관련 쿠키까지 같이 삭제해야 완전히 로그아웃됨

**▶ 로그아웃 설정**

```java
.logout(logout -> logout
    .logoutUrl("/member/logout")
    .logoutSuccessUrl("/")
    .invalidateHttpSession(true)
    .deleteCookies("remember-me", "JSESSIONID")
)
```

- `invalidateHttpSession(true)`: 세션 자체를 무효화
- `deleteCookies(...)`: 지정한 이름의 쿠키들을 브라우저에서 삭제하도록 응답에 반영

---

**▶ 로그인 성공 시 세션에 사용자 정보 저장 (`AuthenticationSuccessHandler`)**

```java
@Component
@RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final MemberMapper mapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        MemberVO vo = mapper.memberInfoData(authentication.getName());
        HttpSession session = request.getSession();
        session.setAttribute("userid", vo.getUserid());
        session.setAttribute("username", vo.getUsername());
        session.setAttribute("sex", vo.getSex());
        response.sendRedirect("/");
    }
}
```

- `authentication.getName()`으로 로그인 성공한 사용자의 아이디를 꺼내서, 화면에서 자주 쓸 값(이름, 성별 등)을 **세션에 직접 저장**해두면 이후 화면에서 `${session.username}`처럼 매번 DB 조회 없이 바로 씀
- 이전 예시(`@AuthenticationPrincipal`로 컨트롤러에서 꺼내는 방식)와 달리, 여기서는 로그인 시점에 한 번만 조회해서 세션에 박아두는 방식

**▶ 로그인 실패 - `redirect` 대신 `forward`로 처리**

```java
@Component
public class LoginFailHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String errorMsg = "";
        if (exception instanceof BadCredentialsException) {
            errorMsg = "아이디나 비밀번호가 틀립니다!!";
        } else if (exception instanceof InternalAuthenticationServiceException) {
            errorMsg = "아이디나 비밀번호가 틀립니다!!";
        } else if (exception instanceof DisabledException) {
            errorMsg = "휴먼 계정입니다!!";
        }
        request.setAttribute("message", errorMsg);
        request.getRequestDispatcher("/member/login").forward(request, response);
    }
}
```

- 이전 버전은 세션에 메시지를 담고 `sendRedirect`(URL이 바뀌며 재요청)했는데, 여기서는 `request.setAttribute` + `getRequestDispatcher(...).forward(...)`로 **같은 요청 안에서 바로 로그인 화면으로 전달**
- `forward`는 URL이 안 바뀌고, `request` 속성이 그대로 유지되므로 `[[${message}]]`처럼 Model 없이도 바로 값을 꺼내 쓸 수 있음 (redirect였다면 request가 초기화돼서 세션에 담아야 했음)

---

**▶ 컨트롤러에서 쿠키 저장 + `RedirectAttributes`로 값 넘기기**

```java
@GetMapping("/food/detail_before")
public String food_detail_before(@RequestParam("no") int no,
        HttpServletResponse response, RedirectAttributes ra) {
    Cookie cookie = new Cookie("food_" + no, String.valueOf(no));
    cookie.setPath("/");            // 사이트 전체에서 유효
    cookie.setMaxAge(60 * 60 * 24); // 1일 보관
    response.addCookie(cookie);     // 브라우저로 전송

    ra.addAttribute("no", no);      // redirect 대상에게 쿼리파라미터로 값 전달
    return "redirect:/food/detail";
}
```

- `HttpServletResponse`를 매개변수로 받아 `Cookie` 객체를 만들고 `addCookie`로 응답에 실어 보냄 (`setPath`로 유효 범위, `setMaxAge`로 보관 기간 지정)
- `redirect:`로 이동할 때는 `Model`에 담은 값이 사라지므로, `RedirectAttributes.addAttribute(...)`로 넘기면 **자동으로 쿼리파라미터**(`?no=1`)로 붙여서 다음 요청에 전달됨
