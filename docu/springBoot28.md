# Spring Security - 인증 처리 전체 흐름 (개념도)

**▶ 로그인 요청부터 인증 완료까지, 내부적으로 어떤 객체들을 거치는지**

```
1. 사용자가 로그인 폼 제출
      → username(ID), password(PW)가 formLogin으로 전송됨

2. UsernamePasswordAuthenticationFilter가 요청을 가로챔
      → loginProcessingUrl로 지정한 주소로 오는 POST 요청을
        컨트롤러 대신 이 필터가 먼저 잡아챔

3. UsernamePasswordAuthenticationToken(인증 시도용 객체) 생성
      → 아직 인증 전 상태의 "이 아이디/비번으로 인증해줘" 요청 객체

4. 이 토큰을 AuthenticationManager에게 전달
      → AuthenticationManager는 직접 인증하지 않고 AuthenticationProvider에게 위임

5. AuthenticationProvider가 UserDetailsService를 호출해서 DB에서 사용자 조회
      → loadUserByUsername(아이디)로 실제 회원 정보(UserDetails)를 가져옴

6. 조회된 사용자 정보로 아이디/비밀번호 일치 여부 확인
      → PasswordEncoder로 입력한 비밀번호와 DB의 암호화된 비밀번호를 비교

7. 인증 성공 시 → SecurityContext에 인증 정보(Principal) 저장
      → 이후 요청부터는 이 SecurityContext를 통해 "로그인된 사용자"로 인식됨
```

**▶ 각 구성요소가 서로 어떻게 연결되는지**

| 구성요소 | 담당 |
|---|---|
| `UsernamePasswordAuthenticationFilter` | 로그인 요청 자체를 가로채는 필터 (formLogin이 자동 등록) |
| `UsernamePasswordAuthenticationToken` | "이 아이디/비번으로 인증해줘"라는 인증 시도 객체 |
| `AuthenticationManager` | 인증 요청을 받아서 적절한 `AuthenticationProvider`에게 위임하는 관리자 |
| `AuthenticationProvider` | 실제 인증 로직을 수행 (기본 제공되는 `DaoAuthenticationProvider`가 내부적으로 이 역할) |
| `UserDetailsService` | DB(또는 다른 저장소)에서 사용자 정보를 조회해서 `UserDetails`로 반환 |
| `PasswordEncoder` | 입력한 비밀번호와 저장된 (암호화된) 비밀번호를 비교 |
| `SecurityContext` | 인증이 완료된 사용자 정보(Principal)를 담아두는 곳 — 이후 요청에서 "로그인 상태"를 판단하는 기준 |

- 개발자가 직접 만드는 건 보통 `UserDetailsService`(회원 조회 로직)와 `PasswordEncoder`(암호화 방식 선택) 정도이고, `AuthenticationManager`/`AuthenticationProvider`/`Filter`는 Security가 내부적으로 이미 구현해둔 걸 그대로 사용하는 경우가 많음
- 이 흐름을 알아두면, "로그인이 왜 안 되는지" 디버깅할 때 **어느 단계에서 막혔는지**(필터가 요청을 못 가로챘는지 / UserDetailsService가 null을 반환했는지 / 비밀번호 인코딩 방식이 안 맞는지)를 순서대로 짚어볼 수 있음
