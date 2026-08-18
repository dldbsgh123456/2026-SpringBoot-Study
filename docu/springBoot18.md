# Java - 람다식(Lambda)

**▶ 람다식 기본 문법**

```java
// 기존 방식 - 익명 클래스로 인터페이스 구현
Runnable r = new Runnable() {
    @Override
    public void run() {
        System.out.println("Thread 실행!!");
    }
};

// 람다식
Runnable r = () -> System.out.println("Thread 실행!!");
new Thread(r).start();
```

- `(매개변수) -> {실행문}` 형태의 **익명 함수** — 클래스 이름도, `class` 파일도 따로 안 만듦
- 매개변수 타입은 생략 가능: `(int a) -> a` 대신 `a -> a`
- 실행문이 한 줄이면 `{ return ... }` 대신 표현식만 써도 됨: `{return a+1}` → `a+1`
- 람다식은 **메소드가 딱 1개인 인터페이스/클래스**에만 적용 가능

**▶ 함수형 인터페이스 (`@FunctionalInterface`)**

```java
@FunctionalInterface
interface Calc {
    int sum(int a, int b);   // 추상 메소드가 반드시 1개만
}

Calc c = (a, b) -> a + b;
System.out.println(c.sum(10, 20));
```

- 람다식은 **함수형 인터페이스**(추상 메소드 1개짜리)만 대입 가능
- `@FunctionalInterface`를 붙이면 "이 인터페이스는 람다용"이라는 의도를 명시하고, 실수로 메소드를 2개 이상 추가하면 컴파일 에러로 잡아줌

**▶ 람다 vs 일반 클래스(익명 클래스) 비교**

| 구분 | 일반(익명) 클래스 | 람다식 |
|---|---|---|
| `.class` 파일 | 생성됨 | 생성 안 됨 |
| 메모리 | 상대적으로 큼 | 작음 |
| 재사용 | 어려움 | 쉬움 |
| 가독성 | 구조가 명확 | 코드가 많아지면 분석이 어려울 수 있음 |

---

**▶ 컬렉션 반복 - `forEach`로 대체**

```java
// 기존 for문
for (String s : list) {
    System.out.println(s);
}

// 람다 + forEach
list.forEach(s -> System.out.println(s));
// 메소드 참조로 더 줄이면
list.forEach(System.out::println);
```

---

**▶ Stream API - 중간 연산 / 최종 연산**

```
중간 연산(체이닝 가능) : filter, map, sorted, distinct
최종 연산(스트림 종료)  : forEach, collect, count, reduce, average
```

```java
List<String> list = List.of("java", "oracle", "html", "jsp", "spring");

// sorted - 정렬 (Comparator를 람다로)
list.stream()
    .sorted((a, b) -> a.length() - b.length())
    .forEach(System.out::println);

// filter - 조건 검색
List<Integer> nList = List.of(1,2,3,4,5,6,7,8);
nList.stream()
     .filter(n -> n % 2 == 0)
     .forEach(System.out::println);

// map - 데이터 변환 (문자열 → 길이)
list.stream()
    .map(w -> w.length())
    .forEach(System.out::println);
```

- `filter`: 조건에 맞는 것만 남김 (SQL의 `WHERE`)
- `map`: 요소를 다른 값/타입으로 변환 (SQL의 `SELECT 변환된컬럼`)
- `sorted`: 정렬 (SQL의 `ORDER BY`) — `Comparator.comparing(필드참조)`로도 작성 가능, `.reversed()`로 내림차순
- `distinct`: 중복 제거

```java
list.stream()
    .sorted(Comparator.comparing(EmpVO::getSal))         // 오름차순
    .forEach(...);

list.stream()
    .sorted(Comparator.comparing(EmpVO::getSal).reversed()) // 내림차순
    .forEach(...);
```

**▶ 집계 - `reduce` / `average`**

```java
// 합계
int total = list.stream()
                 .map(EmpVO::getSal)
                 .reduce(0, Integer::sum);

// 평균
double avg = list.stream()
                  .mapToInt(EmpVO::getSal)
                  .average()
                  .orElse(0);
```

- `reduce(초기값, 연산)`: 스트림 전체를 하나의 값으로 누적 계산 (합계 등)
- `mapToInt(...)`로 기본형 스트림(`IntStream`)으로 바꾸면 `.average()`, `.sum()` 같은 통계 메소드를 바로 쓸 수 있음
- `.average()`는 결과가 `Optional<Double>`이라 `.orElse(0)`으로 값이 없을 때의 기본값을 지정

**▶ 결과를 다시 List로 묶기 - `toList()` / `Collectors`**

```java
List<String> cList = colors.stream()
        .filter(c -> c.startsWith("b"))
        .map(String::toUpperCase)
        .toList();   // 스트림 결과를 List로 변환
```

- `forEach`로 바로 출력하지 않고, 필터링/변환된 결과를 **다시 리스트로 담아서 재사용**하고 싶을 때 `.toList()`(또는 `.collect(Collectors.toList())`) 사용

**▶ 메소드 참조(Method Reference) 정리**

| 형태 | 예시 | 의미 |
|---|---|---|
| `클래스::인스턴스메소드` | `EmpVO::getSal` | 각 요소의 `getSal()` 호출과 동일 |
| `객체::메소드` | `System.out::println` | `System.out.println(x)`와 동일 |
| `클래스::정적메소드` | `Integer::sum` | `Integer.sum(a,b)`와 동일 |

- 람다식이 "매개변수를 받아서 그대로 어떤 메소드에 넘기기만 하는" 경우, `w -> w.length()` 대신 `String::length`처럼 **메소드 참조**로 더 짧게 쓸 수 있음

---

**▶ JDBC로 읽은 결과를 Stream으로 가공하는 실전 예시**

```java
EmpDAO dao = new EmpDAO();
List<EmpVO> list = dao.empAllData();   // JDBC로 DB 조회한 결과

list.stream()
    .filter(vo -> vo.getSal() >= 3000)
    .forEach(vo -> System.out.println(vo.getEname() + " " + vo.getSal()));
```

- MyBatis/JPA 없이 순수 JDBC(`Connection`, `PreparedStatement`, `ResultSet`)로 조회한 `List<EmpVO>`도, 일단 List로만 만들어지면 이후는 **똑같이 Stream으로 필터링/정렬/집계** 가능 — 데이터 출처(JDBC/MyBatis/JPA)와 무관하게 Stream 연산은 동일하게 적용됨
