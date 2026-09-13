# 5일차 D - 발견된 버그/이슈 정리

**▶ ① `authenticationManager` Bean에 `null`을 직접 전달**

```java
@Bean
public AuthenticationManager authenticationManager(HttpSecurity http, BCryptPasswordEncoder passwordEncoder) throws Exception {
    AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
    builder
        .userDetailsService(null)      // ← 문제 지점
        .passwordEncoder(passwordEncoder);
    return builder.build();
}
```

- `.userDetailsService(null)`로 넘기면, 정작 아래에서 따로 등록해둔 `jdbcUserDetailsManager()` Bean이 인증 과정에 **실제로 연결되지 않을 수 있음**
- 의도한 대로 동작하게 하려면 `null` 대신 실제 구현체를 넘겨야 함:
  ```java
  .userDetailsService(jdbcUserDetailsManager())
  .passwordEncoder(passwordEncoder)
  ```

---

**▶ ② `LoginFailureHandler` - `forward`가 `try` 블록이 아니라 `catch` 블록 안에 있음**

```java
@Override
public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException exception) throws IOException, ServletException {
    String errMsg = "";
    try {
        if (exception instanceof BadCredentialsException) {
            errMsg = "아이디나 비밀번호가 틀립니다!!";
        } else if (exception instanceof DisabledException) {
            errMsg = "휴면 계정입니다";
        }
    } catch (Exception ex) {
        // ← 여기 안에 forward 코드가 들어가 있음
        request.setAttribute("message", errMsg);
        request.getRequestDispatcher("/member/login").forward(request, response);
    }
}
```

- `if/else if`만 있는 `try` 블록 안에서는 예외가 발생할 상황이 사실상 없기 때문에, **`catch` 블록 안의 `forward` 코드가 거의 실행되지 않음**
- 결과적으로 로그인 실패 시 `errMsg`는 정상적으로 채워지지만, **로그인 페이지로 돌려보내는 `forward` 자체가 호출이 안 돼서** 사용자에게 실패 메시지가 전달되지 않을 가능성이 높음
- `forward` 호출은 `catch`가 아니라 **`try` 블록이 끝난 뒤(정상 흐름)** 실행되도록 옮겨야 함:
  ```java
  try {
      if (exception instanceof BadCredentialsException) {
          errMsg = "아이디나 비밀번호가 틀립니다!!";
      } else if (exception instanceof DisabledException) {
          errMsg = "휴면 계정입니다";
      }
  } catch (Exception ex) {
      // 정말 예외 상황일 때만 처리
  }
  request.setAttribute("message", errMsg);
  request.getRequestDispatcher("/member/login").forward(request, response);
  ```

---

**▶ ③ `LoginSuccessHandler` - 로그인 후 사용자 정보를 세션에 저장하는 코드가 주석만 있고 없음**

```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
        Authentication authentication) throws IOException, ServletException {
    // SecurityContext => Principal
    // => id (username), password, enabled, authority
    // HttpSession => 저장 => MyBatis이용
    response.sendRedirect("/");   // 실제로는 리다이렉트만 하고 끝남
}
```

- 주석에는 "인증 정보를 세션에 저장한다"는 계획이 적혀 있지만, 실제 구현부에는 리다이렉트 코드 한 줄만 있고 **세션 저장 로직이 비어있음**
- 화면에서 로그인한 사용자 이름 등을 보여주려면, `authentication.getName()`으로 아이디를 꺼내 세션에 담는 코드가 추가로 필요함(이전에 다룬 `LoginSuccessHandler` 예시처럼 `session.setAttribute(...)` 호출 필요)

---

**▶ 요약**

| 위치 | 문제 | 영향 |
|---|---|---|
| `authenticationManager` Bean | `userDetailsService(null)` | 커스텀 `UserDetailsService`가 인증에 반영 안 될 수 있음 |
| `LoginFailureHandler` | `forward`가 `catch` 안에 있음 | 로그인 실패 메시지가 사용자에게 전달 안 될 가능성 |
| `LoginSuccessHandler` | 세션 저장 로직 없음(주석만 있음) | 로그인 후 화면에서 사용자 정보 활용 불가 |

세 가지 모두 **"의도(주석/설계)는 맞는데 구현이 안 됐거나 위치가 잘못된"** 유형의 이슈라, 로직 자체를 새로 짤 필요는 없고 코드 위치/인자만 바로잡으면 해결돼요.
