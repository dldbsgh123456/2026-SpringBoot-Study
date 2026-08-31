# Spring Boot - Kafka 메시징

**▶ Kafka - 서버 간 대량 이벤트를 안정적으로 전달하는 분산 스트리밍**

```
사용자 A가 주문
     │
 주문 서버
     │
 주문 완료 이벤트
     │
   Kafka
     │
 ┌───┴────┬─────────┐
재고서비스  결제서비스  알림서비스
```

- 한 서비스에서 발생한 이벤트(예: "주문 완료")를 **여러 다른 서비스가 동시에 구독해서 각자 처리**해야 할 때 씀 (MSA/이벤트 기반 아키텍처의 핵심 인프라)
- HTTP로 서비스끼리 직접 호출하면, 호출받는 서비스 중 하나라도 느리거나 죽으면 전체가 영향받는데, Kafka를 거치면 **각 서비스가 자기 속도에 맞춰 메시지를 나중에 읽어가도** 됨(비동기, 느슨한 결합)

**▶ 핵심 구성요소**

| 구성요소 | 역할 |
|---|---|
| Producer | Kafka에 메시지를 **보내는** 애플리케이션 (`send`) |
| Topic | 메시지를 종류별로 분류해서 저장하는 공간(채널) |
| Partition | Topic 하나를 여러 개로 쪼갠 단위 — 병렬 처리와 순서 보장의 기본 단위 |
| Consumer | Kafka로부터 메시지를 **읽어서 처리**하는 애플리케이션 |
| Consumer Group | Consumer들을 묶는 그룹 — 같은 그룹 안에서는 메시지를 나눠 가져가고(부하 분산), 그룹이 다르면 같은 메시지를 각자 독립적으로 다 받음 |

```
Producer → Topic(P1, P2, P3로 파티션 분할 저장) → Consumer
```

---

**▶ Producer - 메시지 보내기 (`KafkaTemplate`)**

```java
@RestController
@RequiredArgsConstructor
public class KafkaController {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping("/send")
    public String send() {
        kafkaTemplate.send("test-topic", "Hello Kafka!!");   // (토픽명, 메시지)
        return "Kafka에서 메세지 완료";
    }
}
```

- `KafkaTemplate<String, String>`: 첫 번째 제네릭은 메시지의 **키 타입**, 두 번째는 **값(메시지 본문) 타입** — 여기선 둘 다 문자열
- `send(토픽명, 메시지)`를 호출하는 순간 Kafka 브로커로 메시지가 전송됨 — 호출한 쪽(Producer)은 이 메시지를 누가 언제 가져가서 처리하는지 신경 쓰지 않음(비동기)

---

**▶ Consumer - 메시지 받기 (`@KafkaListener`)**

```java
@Component
public class KafkaManager {
    @KafkaListener(topics = "test-topic", groupId = "test-group")
    public void receive(String message) {
        System.out.println("Kafka 메세지 수신:" + message);
    }
}
```

- `@KafkaListener(topics="...", groupId="...")`가 붙은 메소드는 지정한 토픽에 새 메시지가 올 때마다 **자동으로 호출**됨 (별도 폴링 코드 작성 불필요)
- `groupId`: 이 Consumer가 속한 그룹 이름 — 같은 `groupId`를 가진 Consumer 인스턴스를 여러 개 띄우면, Kafka가 파티션을 나눠서 각 인스턴스에 분배(부하 분산). 서로 다른 `groupId`를 쓰면 동일한 메시지를 각 그룹이 독립적으로 한 번씩 다 받음(예: 재고서비스 그룹과 알림서비스 그룹이 같은 주문 이벤트를 각자 받는 구조)

---

**▶ `application.yml` 설정**

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092   # Kafka 브로커 접속 주소
```

- `bootstrap-servers`: 애플리케이션이 최초로 접속할 Kafka 브로커 주소(포트) — 이 프로젝트의 yml에는 주석 처리(`#`)되어 있어서, 실제로 Kafka를 붙이려면 **주석을 풀고 브로커 주소를 지정**해야 정상 동작함
- 브로커가 여러 대인 클러스터 환경에서는 `localhost:9092,localhost:9093`처럼 콤마로 여러 개 나열 가능 (하나만 지정해도 나머지는 자동으로 인식됨)

