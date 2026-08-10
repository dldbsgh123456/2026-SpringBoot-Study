# 파일 업로드
application.yml 추가
```
server:
  servlet:
    context-path: /
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
```
 server.servlet.context-path - 애플리케이션의 기본 URL 설정 기본값이 / 이긴 해서 없어도 괜찮음
 spring.servlet.multipart.enabled:true - 파일 업로드 처리 활성화 Spring-Boot는 기본값이 true 명시적으로 써 놓음
 spring.servlet.multipart.amx-file-size - 업로드 파일 하나당 최대 100MB
 spring.servlet.multipart.amx-file-size - 한 번의 요청(파일 중복 가능)으로 최대 용량 100MB
 
