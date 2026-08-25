# Spring Boot - WebSocket(STOMP) 실시간 채팅

- HTTP는 요청-응답 한 번으로 끝나는 단방향 통신이라, 실시간 채팅처럼 **서버가 먼저 클라이언트에게 메시지를 밀어 넣어야** 하는 경우엔 맞지 않음
- WebSocket은 한 번 연결을 맺으면 **서버 ↔ 클라이언트 양방향으로 계속 데이터를 주고받을 수 있는** 연결을 유지함
- STOMP는 WebSocket 위에서 "구독(subscribe)/발행(publish)" 개념으로 메시지를 주고받게 해주는 프로토콜 — 순수 WebSocket보다 채팅방/토픽 단위 라우팅이 편리해짐

---

**▶ WebSocket 설정 (`WebSocketMessageBrokerConfigurer`)**

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 최초 연결할 엔드포인트
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();   // WebSocket 미지원 브라우저 대비 폴백
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");     // 서버 → 클라이언트
        registry.setApplicationDestinationPrefixes("/app");   // 클라이언트 → 서버
        registry.setUserDestinationPrefix("/user");            // 1:1 메시지용
    }
}
```

- `registerStompEndpoints`: 클라이언트가 웹소켓 연결을 시작할 **접속 주소**(`/ws-chat`)를 등록. `withSockJS()`는 브라우저가 WebSocket을 지원 안 할 때 자동으로 다른 방식(polling 등)으로 대체해주는 라이브러리
- `enableSimpleBroker("/topic","/queue")`: 이 경로로 시작하는 메시지는 서버가 **구독 중인 클라이언트들에게 그대로 전달**(브로드캐스트/큐잉)
- `setApplicationDestinationPrefixes("/app")`: 클라이언트가 서버로 메시지를 보낼 때는 이 prefix로 시작하는 주소로 보내야 컨트롤러(`@MessageMapping`)가 받음
- `setUserDestinationPrefix("/user")`: 특정 사용자 한 명에게만 보내는 1:1 메시지 전용 prefix

---

**▶ 채팅 메시지 처리 (`@MessageMapping`)**

```java
@Controller
@RequiredArgsConstructor
public class ChatController {
    private final SimpMessagingTemplate template;

    // 전체 채팅 - 구독 중인 모든 사람에게 브로드캐스트
    @MessageMapping("/chat/public")     // 클라이언트가 /app/chat/public 으로 보내면 여기서 받음
    @SendTo("/topic/chat")               // 리턴값을 /topic/chat 구독자 전체에게 전송
    public ChatMessage publicChat(ChatMessage msg, HttpSession session) {
        msg.setSender((String) session.getAttribute("userid"));  // 로그인 세션에서 아이디 꺼내 세팅
        return msg;   // 리턴하면 Jackson이 자동으로 JSON 변환해서 전송
    }

    // 1:1 채팅 - 특정 사용자에게만 전송
    @MessageMapping("/chat/private")
    public void privateChat(ChatMessage msg, HttpSession session) {
        String sender = (String) session.getAttribute("userid");
        msg.setSender(sender);
        template.convertAndSendToUser(msg.getReceiver(), "/queue/chat", msg);  // 상대방에게
        template.convertAndSendToUser(sender, "/queue/chat", msg);              // 나에게도(내 화면에도 표시하려고)
    }
}
```

- `@MessageMapping`은 `@GetMapping`/`@PostMapping`의 WebSocket 버전 — HTTP 요청이 아니라 **STOMP 메시지**를 받는 진입점
- `@SendTo`가 붙으면 메소드의 리턴값을 지정한 주소(`/topic/chat`)의 **구독자 전원에게 자동 전송** — 전체 채팅방처럼 여러 명이 받아야 할 때 사용
- 1:1 채팅은 `@SendTo` 대신 `SimpMessagingTemplate.convertAndSendToUser(받는사람, 주소, 메시지)`를 직접 호출 — 특정 한 사람에게만 보내고, 보낸 사람 자신에게도 한 번 더 보내야 **자기 화면에도 자기가 보낸 메시지가 표시**됨 (자동으로 안 보이기 때문)
- `HttpSession`을 그대로 매개변수로 받아서 로그인 정보(`userid`)를 꺼내 씀 — 채팅 메시지에 실제 로그인한 사람 아이디를 강제로 세팅해서, 클라이언트가 보낸 값이 아니라 **서버가 신뢰할 수 있는 sender 값**을 사용

---

**▶ 클라이언트 (STOMP + SockJS + Vue/Pinia)**

```js
// chatStore.js (요약 개념)
connect() {
    const socket = new SockJS('/ws-chat')
    const stompClient = Stomp.over(socket)
    stompClient.connect({}, () => {
        stompClient.subscribe('/topic/chat', (msg) => { /* 전체 채팅 수신 처리 */ })
        stompClient.subscribe('/user/queue/chat', (msg) => { /* 1:1 채팅 수신 처리 */ })
    })
}
```

- `SockJS('/ws-chat')`로 서버가 등록해둔 엔드포인트에 연결하고, `Stomp.over(socket)`으로 STOMP 클라이언트를 감쌈
- `subscribe(주소, 콜백)`으로 특정 주소를 구독해두면, 서버가 그 주소로 보낸 메시지가 실시간으로 콜백에 들어옴 (`/topic/chat`은 전체 채팅, `/user/queue/chat`은 나에게 온 1:1 메시지)
- 로그인/채팅목록 상태도 이전 Recipe/Food 예시와 동일하게 **Pinia store**(`useChatStore()`)로 분리해서 관리

---

**▶ 채팅 Pinia Store 자세히 - 방 구분 / 접속자 목록 / 스크롤 처리**

```js
const useChatStore = defineStore('chat', {
    state: () => ({
        stomp: null,
        users: [],            // 접속자 목록
        messages: [],          // 현재 화면에 출력 중인 메시지
        publicMessages: [],    // 전체 채팅 메시지
        privateMessages: [],   // 1:1 채팅 메시지 (방ID별로 분리 저장)
        currentRoom: 'public',
        loginUser: '',
        chatBodyEl: null,
        msg: ''
    }),
    actions: {
        // 1:1 방 ID를 두 사람이 항상 같은 값으로 만들기
        makeRoomId(user1, user2) {
            return [user1, user2].sort().join('_')
        },
        changeRoom(user) {
            if (user === 'public') {
                this.currentRoom = 'public'
                this.messages = this.publicMessages
            } else {
                const roomId = this.makeRoomId(this.loginUser, user)
                if (!this.privateMessages[roomId]) {
                    this.privateMessages[roomId] = []
                }
                this.messages = this.privateMessages[roomId]
            }
            this.scrollToBottom()
        },
        async scrollToBottom() {
            await nextTick()   // DOM이 갱신된 다음 시점까지 기다림
            if (this.chatBodyEl) {
                this.chatBodyEl.scrollTop = this.chatBodyEl.scrollHeight
            }
        },
        connect() {
            const socket = new SockJS('/chat-ws')
            this.stomp = Stomp.over(socket)
            this.stomp.connect({}, () => {
                this.stomp.subscribe('/topic/users', msg => {
                    const users = JSON.parse(msg.body)
                    this.users = users.filter(u => u !== this.loginUser)  // 본인은 목록에서 제외
                })
            })
        }
    }
})
```

- **`makeRoomId`**: `kim`과 `hong`이 채팅할 때, 누가 먼저 클릭하든(`kim→hong` 또는 `hong→kim`) 같은 방 ID(`hong_kim`)가 나오도록 두 아이디를 **정렬(`sort`) 후 합쳐서** 방을 구분 — 정렬 안 하면 같은 대화인데 `kim_hong`, `hong_kim` 두 개의 다른 방으로 취급될 위험이 있음
- **`privateMessages`**: 1:1 메시지를 방ID를 key로 하는 객체에 각각 저장해서, 상대방을 바꿔가며 대화해도 **각 상대방과의 대화 내역이 서로 섞이지 않게** 분리 보관
- **`scrollToBottom` + `nextTick`**: 새 메시지가 추가되면 `messages` 배열은 즉시 바뀌지만, 실제 DOM(화면)에 그 메시지가 그려지는 건 다음 렌더링 사이클 — `await nextTick()`으로 **DOM이 실제로 갱신된 다음**에 스크롤을 맨 아래로 내려야 정확히 마지막 메시지 위치로 스크롤됨 (바로 스크롤하면 아직 안 그려진 상태라 한 박자 늦게 동작)
- **접속자 목록**: 서버가 `/topic/users`로 현재 접속자 목록을 브로드캐스트하면, 클라이언트는 그걸 구독해서 받은 목록에서 **자기 자신은 필터링해서 제외**하고 화면에 표시
**

```html
<script th:inline="javascript">
const LOGIN_USER = /*[[${session.userid}]]*/ ''
</script>
```

- 이전에 썼던 `th:inline="javascript"` + 주석 치환 패턴을 여기서도 그대로 사용 — 세션에 저장된 로그인 아이디를 채팅 화면의 초기 JS 변수로 넘겨서, Vue store가 "내가 누구인지"를 알 수 있게 함
