# Spring Security - 인메모리(하드코딩) 기본 설정

**▶ DB 없이 하드코딩된 사용자로 먼저 테스트하는 버전**

```java
public class CustomUserDetailService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) {
        if (username.equals("admin")) {
            return User.builder()
                    .username("admin")
                    .password("{noop}1234")   // {noop} = 암호화 없이 평문 그대로 비교
                    .roles("ADMIN")
                    .build();
        }
        return User.builder()
                .username("user")
                .password("{noop}1234")
                .roles("USER")
                .build();
    }
}
```

- DB/Mapper 연동 없이, 아이디가 `admin`이면 무조건 `ADMIN` 권한, 그 외엔 전부 `USER` 권한을 부여하는 **테스트용 임시 버전** — 실제 서비스에서는 이후 버전(Mapper로 DB 조회하는 `CustomUserDetailsService`)으로 교체됨
- `{noop}`: Spring Security 5부터 비밀번호 앞에 인코딩 방식을 접두어로 붙이는 게 필수가 됐는데, `{noop}`은 "암호화 안 함(No Operation)"이라는 뜻 — 입력한 비밀번호와 문자열 그대로 비교. 실제 서비스에서는 반드시 `{bcrypt}`(= `BCryptPasswordEncoder`)로 암호화해야 함

**▶ 최소 구성 `SecurityConfig` - 실패 처리를 URL로만 지정**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/","/login").permitAll()
                .requestMatchers("/user").authenticated()
                .requestMatchers("/admin").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login_process")
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error")   // 핸들러 클래스 대신 URL만 지정
                .permitAll()
            )
            .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }
}
```

- 이전에 다룬 버전은 `failureHandler(loginFailHandler())`로 **실패 원인별 메시지를 직접 처리**했는데, 이 버전은 `failureUrl("/login?error")`로 **실패 시 이동할 URL만 지정**하는 훨씬 단순한 방식 — 세밀한 실패 메시지가 필요 없는 초기 단계/학습용 설정에 적합
- `PasswordEncoder` Bean 등록이나 `AuthenticationFailureHandler` Bean도 없음 — 딱 "로그인/로그아웃/권한 URL 분기"까지만 다루는 가장 기본형 구성

**▶ `loginProcessingUrl`이 컨트롤러가 아니라 Security가 가로채는 URL이라는 점**

```java
.loginProcessingUrl("/login_process")
// SpringSecurity에서 /login_process로 온 POST 요청은
// 개발자가 만든 Controller가 처리하는 게 아니라
// Security 내부 필터(UsernamePasswordAuthenticationFilter)가 가로채서 인증 처리
```

- `login.html`의 `<form action="/login_process">`로 폼이 전송되면, 이 URL에 대응하는 `@PostMapping` 컨트롤러를 따로 만들 필요가 없음 — Security가 필터 단계에서 이미 요청을 가로채서 인증 로직을 수행하기 때문
