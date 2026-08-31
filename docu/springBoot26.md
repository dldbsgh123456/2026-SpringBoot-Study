# Spring Boot - 게시판(JPA) + 댓글(MyBatis) JPA Entity 옵션

| 기능 | 기술 | 이유 |
|---|---|---|
| 게시글 CRUD | JPA (`BootBoard`, `BootBoardRepository`) | 단순 CRUD라 메소드 규칙/`save()`만으로 충분 |
| 댓글(답변형) | MyBatis (`BoardCommentMapper` + XML) | `group_id`/`group_step` 채번, 서브쿼리 등 동적/복잡 SQL이 필요 |

- "단순 CRUD는 JPA, 복잡한 SQL/대용량 처리는 MyBatis"라는 기준을 실제로 한 프로젝트 안에서 병행 적용한 예시
- 게시글은 `bDao.save(vo)`, `bDao.findByNo(no)`처럼 JPA로 짧게 처리하고, 댓글은 group 계산이 들어가서 MyBatis로 그대로 SQL 제어

---

**▶ JPA Entity 세부 옵션**

```java
@Entity
@Table(name="bootboard")   // 테이블명은 소문자로 (대문자 쓰면 언더스코어 등으로 자동 변환될 수 있음)
@DynamicUpdate              // UPDATE 시 변경된 컬럼만 SQL에 포함 (전체 컬럼 갱신 방지)
@Data
public class BootBoard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // 시퀀스가 있는 DB라면 -> @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="시퀀스명")
    private int no;

    private String name, subject, content;

    @Column(insertable = true, updatable = false)   // 등록 때만 값 저장, 이후 수정 불가
    private String pwd;

    private int hit;

    @Column(insertable = true, updatable = false, name = "regdate")
    private LocalDateTime regdate;

    @PrePersist   // INSERT 되기 직전에 자동 실행되는 생명주기 콜백
    public void perSist() {
        regdate = LocalDateTime.now();
    }
}
```

- `@GeneratedValue(strategy=GenerationType.IDENTITY)`: DB의 자동증가 컬럼(AUTO_INCREMENT/IDENTITY)에 채번을 위임 — 시퀀스를 쓰는 DB(Oracle)라면 `SEQUENCE` 전략 + `generator`로 시퀀스 이름을 지정하는 방식으로 바뀜
- `@DynamicUpdate`: 기본적으로 JPA는 UPDATE할 때 엔티티의 **모든 컬럼**을 SQL에 포함시키는데, 이 옵션을 붙이면 **실제로 값이 바뀐 컬럼만** UPDATE 문에 포함됨 (컬럼이 많을 때 성능/락 경합 이점)
- `@Column(insertable=true, updatable=false)`: 등록 시점에는 값을 저장하지만, 이후 `save()`로 다시 저장해도 **이 컬럼은 UPDATE에서 제외** — 비밀번호나 작성일처럼 "처음 한 번만 정해지고 이후 안 바뀌어야 하는 값"에 사용
- `@PrePersist`: 이 엔티티가 처음 DB에 저장되기(`INSERT`) 직전에 자동으로 호출되는 메소드 — 여기서는 저장 시점의 현재 시각을 `regdate`에 자동으로 채워 넣어서, 컨트롤러에서 직접 날짜를 세팅 안 해도 됨

---

**▶ MyBatis `<selectKey>` - `@SelectKey` 어노테이션의 XML 버전**

```xml
<insert id="boardCommentInsert" parameterType="com.sist.web.vo.BootCommentVO">
  <selectKey keyProperty="no" resultType="int" order="BEFORE">
    SELECT NVL(MAX(no)+1,1) as no
    FROM bootComment
  </selectKey>
  INSERT INTO bootComment(no,board_no,id,name,msg,group_id)
  VALUES(#{no},#{board_no},#{id},#{name},#{msg},
         (SELECT NVL(MAX(group_id)+1,1) FROM bootComment))
</insert>
```

- 이전에 어노테이션으로 썼던 `@SelectKey(before=true, ...)`와 완전히 같은 개념을 XML(`<selectKey order="BEFORE">`)로 표현한 것 — INSERT 전에 번호를 미리 계산해서 `keyProperty`(`no`)에 채워 넣음

---

**▶ Thymeleaf 날짜 포맷 - `#temporals`**

```html
[[${#temporals.format(vo.regdate,'yyyy-MM-dd hh:mm:ss')}]]
```

- 엔티티 필드가 `LocalDateTime`(Java 8 날짜 타입)일 때는 `#temporals.format(값, 패턴)`으로 포맷 — 이전에 문자열로 이미 변환해서 내려주던 `dbday`(예: `TO_CHAR(regdate,...)`를 SQL에서 처리)와 달리, 여기서는 **날짜 타입 그대로 Model에 담고 화면에서 포맷 변환**

---

**▶ 페이지 전체가 아니라 특정 영역에만 Vue 앱 마운트**

```js
// boardView.js
const commentApp = createApp({
    setup() {
        const store = useBoardStore()
        onMounted(() => {
            store.sessionId = SESSION_ID
            store.boardcommentListData(BOARDNO)
        })
        return { store }
    }
})
commentApp.use(createPinia())
commentApp.mount("#comment")   // 페이지 전체가 아니라 댓글 영역(div#comment)에만 마운트
```

```html
<div class="row" id="comment">
  <h3>댓글</h3>
  ...
</div>
```

- 지금까지는 `.mount(".container")`처럼 화면 전체를 Vue가 관리했는데, 여기서는 **게시글 상세 내용은 Thymeleaf가 서버에서 그대로 렌더링**하고, **댓글 영역(`#comment`)만 별도 Vue 앱을 마운트**해서 관리
- 한 페이지 안에 "서버 렌더링 영역"과 "Vue가 관리하는 영역"이 공존 — 게시글 자체는 자주 안 바뀌니 그대로 두고, 댓글처럼 실시간으로 갱신되는 부분만 SPA처럼 동작하게 분리한 설계

**▶ Security 인증 여부에 따라 Vue 입력창 자체를 노출/숨김**

```html
<table class="table" sec:authorize="isAuthenticated()">
  <textarea v-model="store.msg" ref="msgRef"></textarea>
  <button>댓글</button>
</table>
```

- `sec:authorize`(서버 렌더링 시점에 결정)와 `v-model`(클라이언트 Vue 바인딩)이 **같은 태그 안에서 같이 쓰임** — 로그인 안 한 사람에게는 Thymeleaf가 아예 이 영역 자체를 HTML에 렌더링하지 않으므로, Vue 코드가 있어도 로그인 안 하면 입력창 자체가 존재하지 않음

---

**▶ RestController에서 조회 로직을 메소드로 뽑아서 등록 후 재사용**

```java
public Map commonsListData(int page, int board_no) {
    // 목록 + 총페이지 + 개수 조립
    ...
    return map;
}

@GetMapping("/reply/list_vue")
public ResponseEntity<Map> board_list(...) {
    return ResponseEntity.ok(commonsListData(page, board_no));
}

@PostMapping("/reply/insert_vue")
public ResponseEntity<Map> reply_insert(@RequestBody BootCommentVO vo, HttpSession session) {
    ...
    bMapper.boardCommentInsert(vo);
    return ResponseEntity.ok(commonsListData(vo.getPage(), vo.getBoard_no()));  // 등록 후 최신 목록 재조립해서 바로 응답
}
```

- 댓글을 새로 등록한 직후에도 **같은 목록 조회 로직(`commonsListData`)을 재사용**해서, 등록 응답에 최신 댓글 목록을 바로 포함시켜 반환 — 클라이언트가 등록 후 목록을 다시 요청할 필요 없이 한 번의 응답으로 화면을 갱신할 수 있음
- `@RequestBody`로 JSON을 `BootCommentVO`로 받아서, `session`에서 꺼낸 로그인 아이디/이름을 클라이언트가 보낸 값 대신 **서버에서 직접 채워 넣음**(위·변조 방지)

**▶ `@Async` - 비동기 처리**

```java
@Async
@GetMapping("/reply/list_vue")
public ResponseEntity<Map> board_list(...) { ... }
```

- `@Async`가 붙은 메소드는 호출한 쪽의 스레드를 기다리게 하지 않고 **별도 스레드에서 비동기로 실행**됨 (활성화하려면 `@EnableAsync` 설정이 별도로 필요) — 댓글 조회/등록처럼 메인 화면 렌더링과 무관하게 처리해도 되는 요청에 적용
