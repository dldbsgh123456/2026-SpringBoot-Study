# Spring Boot / JPA - 새로 나온 패턴만 정리

**▶ PK가 문자열(String)인 Entity**

```java
@Entity
@Data
// 테이블명이랑 클래스명이 같아서 @Table 어노테이션 안 씀
public class Chef {
    @Id
    private String chef;   // PK가 int가 아니라 String
    private String poster;
    private String mem_cont1, mem_cont3, mem_cont2, mem_cont7;
}
```

```java
public interface ChefRepository extends JpaRepository<Chef, String> {
}
```

- 지금까지 본 `@Id`는 전부 `int no`였는데, 여기서는 `String chef`가 PK
- `JpaRepository<엔티티타입, PK타입>`의 두 번째 제네릭도 PK 타입에 맞춰 `String`으로 지정
- 클래스명(`Chef`)과 실제 테이블명(`Chef`)이 같으면 `@Table(name=...)`을 생략해도 됨 (다르면 꼭 명시해야 함 — 예: `Recipe` 엔티티는 실제 테이블이 `recipe2`라서 `@Table(name="recipe2")`를 명시)

---

**▶ 하나의 Service가 여러 개의 Repository를 조합**

```java
@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private final RecipeRepository rDao;
    private final ChefRepository cDao;   // Repository 두 개를 동시에 주입

    public List<Recipe> recipeListData(int page) { ... rDao... }
    public List<Chef> chefListData(int page) { ... cDao... }
}
```

- 지금까지는 Service 하나에 Repository 하나였는데, 여기서는 레시피(`Recipe`)와 쉐프(`Chef`)라는 서로 다른 엔티티를 하나의 `RecipeService`가 같이 다룸
- 화면 단위(레시피 목록 + 쉐프 목록)로 Service를 나누고, 그 안에서 필요한 Repository를 여러 개 주입받는 구조도 가능하다는 예시

---

**▶ 페이지 정보를 배열(`int[]`) 하나로 묶어서 화면에 전달**

```java
model.addAttribute("pages", pages);   // {curpage, totalpage, startPage, endPage}
```

```html
<li th:if="${pages[2]>1}"><a th:href="@{/main/main(page=${pages[2]-1})}">&laquo;</a></li>
<li th:each="i:${#numbers.sequence(pages[2],pages[3])}" th:class="${i==pages[0]?'active':''}">
    <a th:href="@{/main/main(page=${i})}">[[${i}]]</a>
</li>
<li th:if="${pages[3]<pages[1]}"><a th:href="@{/main/main(page=${pages[3]+1})}">&raquo;</a></li>
```

- 이전까지는 `curpage`, `totalpage`, `startPage`, `endPage`를 각각 `model.addAttribute`로 따로따로 담았는데, 여기서는 `int[]` 배열 하나(`pages`)로 묶어서 한 번만 전달
- Thymeleaf에서는 `${pages[0]}`, `${pages[2]}`처럼 **배열 인덱스로 직접 접근**해서 사용 (0:curpage, 1:totalpage, 2:startPage, 3:endPage 순서를 코드 보면서 기억해야 함 — 이름이 없어서 헷갈리기 쉬운 부분)

---

**▶ `Pageable`에 정렬을 안 넣는 경우도 있음**

```java
Pageable pg = PageRequest.of(page - 1, 20);  // Sort 없이 페이지 크기만 지정
Page<Chef> pList = cDao.findAll(pg);
```

- `recipeListData`에서는 `Sort.by(...)`까지 넣었지만, `chefListData`에서는 정렬 조건 없이 페이지 번호/크기만 넘김 → 정렬이 꼭 필요하지 않으면 생략 가능하다는 걸 보여주는 예시
