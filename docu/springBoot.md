# 게시판 (Board) CRUD — Spring Boot + JPA + Thymeleaf
---

## ▶ 전체 구조

```
브라우저 ──▶ Controller ──▶ Service(interface) ──▶ ServiceImpl ──▶ Repository ──▶ DB
   ▲            │                                                                  │
   └──── View(Thymeleaf) ◀── Model ◀─────────────────────────────────────────────┘
```

| 계층 | 파일 | 역할 |
|---|---|---|
| Entity | `BoardEntity` | `jpaboard` 테이블 매핑, PK는 Oracle 시퀀스(`jpb_no_seq`)로 자동 증가 |
| DTO (조회) | `BoardDTO` | 목록 조회 전용 Projection 인터페이스 (`no, name, subject, dbday, hit`) |
| DTO (요청) | `BoardInsertForm`, `BoardUpdateForm` | 등록/수정 폼 바인딩 전용, Entity를 직접 노출하지 않기 위해 분리 |
| Repository | `BoardRepository` | `JpaRepository` 상속 + 페이징용 native query(`boardListData`) |
| Service | `BoardService` / `BoardServiceImpl` | 비밀번호 검증, 조회수 증가, hit 유지 등 비즈니스 로직 |
| Controller | `BoardController` | 요청 매핑 및 뷰 이름 반환만 담당 |
| Exception | `BoardNotFoundException`, `InvalidPasswordException` | 존재하지 않는 글 / 비밀번호 불일치 처리 |
| View | `list, detail, insert, update, delete, update_ok, delete_ok` | Thymeleaf 템플릿 |

---

## ▶ 폴더 구조

```
src/
 └─ com/sist/web/
     ├─ entity/       BoardEntity
     ├─ vo/           BoardDTO
     ├─ dto/          BoardInsertForm, BoardUpdateForm
     ├─ repository/   BoardRepository
     ├─ service/      BoardService, BoardServiceImpl
     ├─ controller/   BoardController
     └─ exception/    BoardNotFoundException, InvalidPasswordException
templates/
 └─ board/
     ├─ list.html         목록 (페이징)
     ├─ detail.html       상세보기
     ├─ insert.html       등록 폼
     ├─ update.html       수정 폼
     ├─ update_ok.html    수정 결과 처리 (스크립트)
     ├─ delete.html       삭제 폼 (비밀번호 입력)
     └─ delete_ok.html    삭제 결과 처리 (스크립트)
```

---

## ▶ 주요 기능 (URL ↔ View 매핑)

| URL | 메서드 | View | 기능 |
|---|---|---|---|
| `board/list` | GET | `list.html` | 페이징 목록 (10건씩) |
| `board/detail` | GET | `detail.html` | 상세보기 (조회 시 hit +1) |
| `board/insert` | GET | `insert.html` | 등록 폼 |
| `board/insert_ok` | POST | – (redirect) | 등록 처리 → 목록으로 리다이렉트 |
| `board/update` | GET | `update.html` | 수정 폼 (기존 값 채움) |
| `board/update_ok` | POST | `update_ok.html` | 비밀번호 검증 후 수정 → 결과에 따라 alert/이동 |
| `board/delete` | GET | `delete.html` | 삭제 폼 (비밀번호 입력) |
| `board/delete_ok` | POST | `delete_ok.html` | 비밀번호 검증 후 삭제 → 결과에 따라 alert/이동 |

---

## ▶ View 레이어 특징

- **목록(`list.html`)**: `th:each`로 목록 순회, `th:href="@{/board/list(page=...)}"`로 페이징 링크에 쿼리 파라미터 자동 조립. 첫 페이지/마지막 페이지에서는 `curpage>1?curpage-1:curpage` 식 삼항 연산으로 이전/다음 버튼이 범위를 벗어나지 않게 처리.
- **상세(`detail.html`)**: `[[${...}]]` (인라인 표현식)과 `th:text` 두 방식을 혼용 중. 통일하는 게 가독성에 좋음.
- **등록/수정 결과(`update_ok.html`, `delete_ok.html`)**: 화면을 그리지 않고 `th:if="${res=='no'/'yes'}"`로 조건 분기된 `<script>`만 내려줘서 `alert()` 후 `history.back()` 또는 `location.href` 이동. **화면 깜빡임 없이 결과만 처리하는 패턴**인데, 최신 방식이라면 AJAX + fetch로 대체하는 게 더 자연스러움.
- **폼(`insert.html`, `update.html`)**: `required` 속성으로 클라이언트 단 최소 검증. 수정 폼은 `th:value`로 기존 값 채우고, `no`는 `hidden` 필드로 함께 전송.
- **삭제(`delete.html`)**: 비밀번호만 입력받는 별도 확인 폼 → `delete_ok`로 POST.

---

## ▶ Entity 설계 포인트

- `@DynamicInsert` / `@DynamicUpdate` → null 컬럼 제외, 변경된 컬럼만 SQL 생성
- `pwd`, `regdate` → `insertable=true, updatable=false` : 최초 등록 시에만 저장, 이후 수정 불가
- `@PrePersist` → insert 시점에 `regdate`를 `yyyy-MM-dd` 문자열로 자동 세팅
- `hit` → `@ColumnDefault("0")`으로 DB 기본값 지정

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "jpb_no_seq")
private int no;
```

---

## ▶ 초기 버전 → 리팩토링 변경 사항

처음 구현은 동작은 했지만 Entity를 폼 바인딩에 직접 쓰고, 비밀번호 검증 같은 로직이 Controller에 섞여 있었음. 아래와 같이 계층을 분리함.

| 항목 | Before | After |
|---|---|---|
| 폼 바인딩 | `@ModelAttribute BoardEntity vo` | `BoardInsertForm` / `BoardUpdateForm` 별도 DTO |
| 비밀번호 검증 | Controller에서 `if (vo.getPwd().equals(pwd))` | Service에서 검증, 실패 시 `InvalidPasswordException` |
| 존재하지 않는 글 조회 | 처리 없음 → NPE 위험 | `Optional` + `BoardNotFoundException` |
| 상세보기 hit 증가 | `findByNo` → 증가 → 다시 `findByNo` (쿼리 2번) | `save()` 결과를 그대로 반환 (쿼리 1번) |
| 수정 시 hit 유지 | Controller에서 `vo.setHit(dbvo.getHit())` | Service 내부에서 Builder로 조립 |

> ⚠️ 비밀번호는 현재 평문 비교 방식. 학습용 프로젝트라 유지했으나, 실무 적용 시에는 해시 비교로 교체 필요.

---

## ▶ 남은 개선 포인트 (TODO)

- 비밀번호 해시 처리
- Controller의 `board/`, `/board/...` 경로 슬래시 표기 통일
- 예외 처리를 `@ExceptionHandler`에서 커스텀 에러 페이지로 확장
- `update_ok.html` / `delete_ok.html`의 alert 기반 결과 처리를 AJAX 방식으로 개선
- `detail.html`의 `[[${}]]` / `th:text` 표현식 사용 방식 통일
- Bootstrap 3(2019년 이후 미지원) → 최신 버전 또는 다른 CSS 프레임워크로 교체 검토
