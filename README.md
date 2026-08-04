# 게시판 (Board) CRUD — Spring Boot + JPA

Spring Data JPA 기반으로 구현한 게시판 CRUD 프로젝트 정리.

---

## ▶ 기술 스택

| 구분 | 내용 |
|---|---|
| Framework | Spring Boot |
| 데이터 접근 | Spring Data JPA (Hibernate) |
| DB | Oracle (시퀀스 기반 PK) |
| View | Thymeleaf |
| 기타 | Lombok |

---

## ▶ 전체 구조

```
Controller  ──▶  Service (interface)  ──▶  ServiceImpl  ──▶  Repository  ──▶  DB
 (요청/응답)         (기능 규약)              (비즈니스 로직)      (JPA 쿼리)      (jpaboard)
```

| 계층 | 클래스 | 역할 |
|---|---|---|
| Entity | `BoardEntity` | `jpaboard` 테이블 매핑, PK는 Oracle 시퀀스(`jpb_no_seq`)로 자동 증가 |
| DTO (조회) | `BoardDTO` | 목록 조회 전용 Projection 인터페이스 (`no, name, subject, dbday, hit`) |
| DTO (요청) | `BoardInsertForm`, `BoardUpdateForm` | 등록/수정 폼 바인딩 전용, Entity를 직접 노출하지 않기 위해 분리 |
| Repository | `BoardRepository` | `JpaRepository` 상속 + 페이징용 native query(`boardListData`) |
| Service | `BoardService` / `BoardServiceImpl` | 비밀번호 검증, 조회수 증가, hit 유지 등 비즈니스 로직 |
| Controller | `BoardController` | 요청 매핑 및 뷰 이름 반환만 담당 |
| Exception | `BoardNotFoundException`, `InvalidPasswordException` | 존재하지 않는 글 / 비밀번호 불일치 처리 |

---

## ▶ 주요 기능 (URL)

| URL | 메서드 | 기능 |
|---|---|---|
| `board/list` | GET | 페이징 목록 (10건씩) |
| `board/detail` | GET | 상세보기 (조회 시 hit +1) |
| `board/insert`, `insert_ok` | GET / POST | 글 등록 |
| `board/delete`, `delete_ok` | GET / POST | 비밀번호 검증 후 삭제 |
| `board/update`, `update_ok` | GET / POST | 비밀번호 검증 후 수정 |

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
