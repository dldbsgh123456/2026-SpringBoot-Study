# Spring Security - JWT 기반 인증 (세션 없는 로그인)

**▶ 세션 방식 vs JWT 방식**

```
[세션 방식]
로그인 → ID/PW 확인 → Session 생성 → JSESSIONID 쿠키 저장
→ 다음 요청마다 Session 확인 → 로그인 사용자 확인
   => 서버가 로그인 상태를 계속 들고 있음 (Stateful)

[JWT 방식]
로그인 → ID/PW 확인 → JWT 생성 → 클라이언트가 JWT 저장(쿠키/localStorage)
→ 다음 요청마다 Authorization: Bearer JWT 헤더(또는 쿠키)로 토큰 전송
→ 서버가 JWT 자체를 검증해서 로그인 사용자 확인
   => 서버는 세션을 안 들고 있음 (Stateless)
```

- 이전에 다룬 `SecurityConfig`(폼 로그인 + 세션)와 가장 큰 차이는 **서버가 로그인 상태를 기억하지 않는다**는 점 — 서버가 여러 대(분산 서버)여도 세션 공유 문제가 없어서 MSA 환경에 유리
- JWT 구조: `xxxxx.yyyyy.zzzzz` = `Header.Payload.Signature` — Payload에 사용자 아이디/권한 같은 실제 정보가 담기고, Signature로 위조 여부를 검증

**▶ 전체 동작 순서**

```
로그인 요청(POST) → AuthController
   → AuthenticationManager.authenticate() : UserDetailsService로 DB에서 사용자 검색, 비번 대조
   → 인증 성공 시 JwtTokenProvider.createToken()으로 JWT 발급 → Cookie에 저장 → /home 응답
다른 페이지 요청 시 → JwtAuthenticationFilter
   → 쿠키(또는 Authorization 헤더)에서 토큰 추출 → 검증 → username 추출
   → UserDetailsService로 사용자 재조회 → SecurityContextHolder에 인증 정보 저장
   → 이후 컨트롤러가 정상적으로 로그인된 사용자 취급
```

---

**▶ `JwtTokenProvider` - 토큰 생성/검증**

```java
@Component
public class JwtAuthenticationProvider {
    private final String SECRET = "my-secret-key-...";  // 실무에서는 application.yml + 환경변수로 분리 권장

    public String createToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)                 // Payload: {sub: "admin"}
                .claim("role", role)                    // Payload: {role: "ROLE_ADMIN"}
                .setIssuedAt(new Date())                 // 발급 시간
                .setExpiration(new Date(System.currentTimeMillis() + 3600000)) // 1시간 후 만료
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))  // SECRET으로 서명
                .compact();
    }

    public String getUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validate(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(SECRET.getBytes()).build().parseClaimsJws(token);
            return true;
        } catch (Exception ex) {
            return false;   // 서명 위조/만료 등 어떤 이유든 실패하면 false
        }
    }
}
```

- `signWith(SECRET)`으로 서명해두면, 나중에 `parseClaimsJws`가 **같은 SECRET으로 서명을 다시 검증** — 토큰 내용이 조금이라도 변조되면 서명이 안 맞아서 예외가 발생 → `validate()`가 `false` 반환
- `setExpiration`으로 만료시간을 못박아두면, 시간이 지난 토큰은 `parseClaimsJws` 단계에서 자동으로 예외(만료) 처리됨

---

**▶ `AuthController` - 로그인 시 JWT 발급 + 쿠키로 전송**

```java
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager manager;
    private final JwtAuthenticationProvider provider;

    @RequestMapping("/member/login")
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        Authentication auth = manager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));  // ID/PW 인증
        UserDetails user = (UserDetails) auth.getPrincipal();
        String role = user.getAuthorities().iterator().next().getAuthority();

        String token = provider.createToken(user.getUsername(), role);

        ResponseCookie cookie = ResponseCookie.from("accessToken", token)
                .httpOnly(true)   // JS에서 document.cookie로 못 읽게 (XSS 방지)
                .secure(false)    // HTTPS 환경이면 true로
                .path("/")
                .maxAge(3600)
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.LOCATION, "/home")
                .build();
    }
}
```

- 인증 자체는 `AuthenticationManager.authenticate(...)`에게 위임 — 내부적으로 `UserDetailsService`를 호출해서 DB 조회 + 비밀번호 대조까지 다 처리해줌 (직접 비교 코드 안 씀)
- `ResponseCookie`로 쿠키를 만들 때 `httpOnly(true)`를 주면 JavaScript(`document.cookie`)로 토큰을 읽을 수 없게 막아서 **XSS 공격으로 토큰이 탈취되는 걸 방지**
- 응답 자체를 `302 FOUND` + `Location: /home` 헤더로 만들어서, 별도 뷰 반환 없이 **리다이렉트 응답을 직접 조립**

---

**▶ `JwtAuthenticationFilter` - 매 요청마다 토큰 검사 (`OncePerRequestFilter`)**

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationProvider provider;

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = null;

        // 1. Authorization 헤더 확인 (Authorization: Bearer xxxxx)
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }
        // 2. 헤더에 없으면 쿠키 확인
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }
        // 3. 토큰이 유효하면 SecurityContext에 인증 정보 등록
        if (token != null && provider.validate(token)) {
            String username = provider.getUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);  // 다음 필터/컨트롤러로 진행
    }
}
```

- `OncePerRequestFilter`: 하나의 요청당 **정확히 한 번만** 실행되도록 보장하는 Security의 필터 베이스 클래스 (내부 forward 등으로 필터가 중복 실행되는 걸 방지)
- 헤더(API 클라이언트용) → 쿠키(브라우저 폼 로그인용) 순서로 토큰을 찾음 — 두 가지 클라이언트 유형을 모두 지원
- 토큰이 유효하면 그때그때 `SecurityContextHolder`에 인증 정보를 채워 넣음 — 세션에 미리 저장해둔 게 아니라 **매 요청마다 토큰으로부터 다시 인증 상태를 복원**하는 것이 세션 방식과의 근본적 차이

---

**▶ `JwtSecurityConfig` - Stateless 설정 + 필터 등록**

```java
@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter filter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 세션 자체를 안 만듦
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/","/login","/member/login").permitAll()
                .requestMatchers("/admin").hasRole("ADMIN")
                .requestMatchers("/user").hasAnyRole("USER","ADMIN","MANAGER")
                .anyRequest().permitAll()
            )
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);  // 커스텀 필터를 먼저 태움
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

- `SessionCreationPolicy.STATELESS`: Security가 세션을 아예 생성하지 않도록 설정 — JWT 방식에서는 필수 (세션을 만들면 JWT를 쓰는 의미가 퇴색됨)
- `addFilterBefore(커스텀필터, UsernamePasswordAuthenticationFilter.class)`: Security의 기본 폼 로그인 필터보다 **먼저** 내가 만든 `JwtAuthenticationFilter`가 실행되도록 순서를 지정 — 토큰 검사를 먼저 하고 그 결과를 SecurityContext에 심어둬야 이후 권한 체크(`hasRole` 등)가 정상 동작
- `hasAnyRole("USER","ADMIN","MANAGER")`: 여러 권한 중 하나라도 있으면 허용 (이전 `hasRole` 하나만 쓰던 것에서 확장)

---

**▶ `CustomUserDetailsService` - 권한을 Stream으로 변환**

```java
List<AuthorityVO> authorityList = mService.getAuthorityData(username);
List<SimpleGrantedAuthority> authorities =
        authorityList.stream()
                .map(a -> new SimpleGrantedAuthority(a.getAuthority()))
                .toList();

return User.builder()
        .username(member.getUserid())
        .password(member.getUserpwd())
        .authorities(authorities)
        .build();
```

- 이전엔 `for`문 + `HashSet`으로 권한을 담았는데, 여기서는 **Stream(`map` + `toList()`)**으로 `AuthorityVO` 리스트를 `SimpleGrantedAuthority` 리스트로 한 줄 변환
- `User.builder()...build()`처럼 빌더 패턴으로 `UserDetails`를 조립하는 방식도 이전의 `new User(...)` 다중 인자 생성자 방식과 다른 스타일(가독성 위주)
- 계정 비활성화(`enable != 1`) 체크를 `User`의 boolean 플래그로 넘기는 대신, **아예 조회 단계에서 예외를 던져 로그인을 막는** 방식으로 처리 (`UsernameNotFoundException`)

---

**▶ 토큰 저장 방식 - `localStorage` vs `httpOnly` 쿠키**

```js
// login.html - fetch로 로그인 후 localStorage에 토큰 저장
async function login() {
    const p = new URLSearchParams();
    p.append("username", username.value);
    p.append("password", password.value);

    const res = await fetch("/member/login", { method: "POST", body: p });
    const token = await res.text();       // 응답 본문으로 토큰을 직접 받음
    localStorage.setItem("token", token);  // 브라우저에 직접 저장
    location.href = "/";
}
```

- 위 `AuthController`는 로그인 성공 시 `ResponseCookie`로 **서버가 쿠키에 토큰을 담아 내려주는 방식**이었는데, 이 화면은 **JS가 fetch로 직접 토큰을 받아서 `localStorage`에 저장**하는 방식 — 같은 JWT여도 서로 다른 보관 전략
- `localStorage`에 저장하면 이후 요청마다 JS가 `Authorization: Bearer <token>` 헤더를 직접 붙여서 보내야 함 (쿠키처럼 브라우저가 자동으로 실어 보내주지 않음)

| 구분 | `httpOnly` 쿠키 | `localStorage` |
|---|---|---|
| 저장 위치 | 브라우저 쿠키 저장소 | 브라우저 로컬 스토리지 |
| JS로 직접 접근 가능 여부 | 불가능 (`httpOnly` 옵션) | 가능 (`localStorage.getItem`) |
| XSS 공격에 대한 노출 | 안전 (스크립트가 못 읽음) | 취약 (악성 스크립트가 토큰을 그대로 읽어갈 수 있음) |
| 요청마다 전송 | 브라우저가 자동으로 실어 보냄 | JS가 매번 헤더에 직접 담아야 함 |
| CSRF 위험 | 있음 (자동 전송되므로) — SameSite 옵션 등 별도 대비 필요 | 상대적으로 낮음 (자동 전송이 안 되므로) |

- 실무에서는 보통 **`httpOnly` 쿠키가 XSS에 더 안전**하다고 권장되지만, CSRF 대비가 별도로 필요함
- `localStorage`는 구현이 간단(모바일/SPA에서 흔함)하지만, 페이지에 악성 스크립트가 하나라도 심어지면 토큰이 그대로 탈취될 수 있어 XSS 방어가 특히 중요해짐
