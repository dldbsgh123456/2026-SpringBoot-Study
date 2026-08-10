# Spring Boot / JPA - 레시피 상세 & 검색 새 패턴

**▶ 목록용 테이블과 상세용 테이블을 분리 (`Recipe` vs `RecipeDetail`)**

```java
@Entity
@Table(name="recipedetail")
@Data
public class RecipeDetail {
    @Id
    private int no;
    private String poster, title, chef, chef_poster, chef_profile,
                   info1, info2, info3, content, foodmake;  // content/foodmake는 CLOB
}
```

- `Recipe`(recipe2 테이블)는 목록에 필요한 가벼운 컬럼만, `RecipeDetail`(recipedetail 테이블)은 본문(CLOB)까지 포함한 무거운 컬럼을 가짐
- 목록 조회는 가벼운 테이블만 쓰고, 상세보기 들어갈 때만 무거운 테이블(`RecipeDetailRepository.findByNo`)을 조회 → 목록 성능을 위한 테이블 분리 설계

---

**▶ 여러 줄 SQL을 Java Text Block(`"""`)으로 작성 + `INTERSECT`로 교집합 조회**

```java
@Query(value="""
      SELECT *
      FROM recipe2
      WHERE no IN(SELECT no FROM recipe2
                            INTERSECT
                            SELECT no FROM recipedetail)
      ORDER BY no DESC
      OFFSET :start ROWS FETCH NEXT 12 ROWS ONLY  
     """, nativeQuery = true)
public List<Recipe> recipeListData(@Param("start") int start);
```

- 이전엔 `+` 문자열 이어붙이기로 SQL을 작성했는데, 여기서는 Java 15+ **Text Block**(`"""`)을 써서 줄바꿈 그대로 SQL을 작성 — 가독성이 좋아짐
- `Recipe`와 `RecipeDetail`이 별도 테이블로 나뉘어 있어서 JPA 연관관계(`@OneToOne` 등)로 묶지 않고, `INTERSECT`로 "두 테이블에 공통으로 존재하는 `no`"만 뽑아 목록에 노출 (상세 데이터가 아직 없는 레시피는 목록에서 제외되는 효과)

---

**▶ 메소드 이름 규칙 + `Pageable` 조합이 `Page<T>`를 바로 반환**

```java
public interface RecipeRepository extends JpaRepository<Recipe, Integer> {
    public Page<Recipe> findByTitleContains(String title, Pageable pg);
    public Page<Recipe> findByChefContains(String chef, Pageable pg);
    public long countByTitleContains(String title);
    public long countBychefContains(String chef);
}
```

```java
Pageable pg = PageRequest.of(page - 1, ROWSIZE, Sort.by(Sort.Direction.ASC, "no"));
Page<Recipe> pList = rDao.findByTitleContains(title, pg);
```

- 지금까지의 `findByXxxContains`는 `List<T>`만 반환했는데, 매개변수 마지막에 `Pageable`을 추가하면 **검색 조건 + 페이징이 자동으로 합쳐진 `Page<T>`**를 바로 돌려줌 (SQL을 직접 안 써도 검색+페이징이 한 번에 처리됨)
- `countByTitleContains`, `countByChefContains`처럼 `count` 계열도 조건을 붙여서 "검색 결과의 총 개수"만 따로 뽑을 수 있음 → 검색 결과 페이징 계산(`getPageDataFind`)에 사용

---

**▶ Thymeleaf `th:each`의 상태 변수(`status`)로 두 리스트를 같이 순회**

```java
String[] makes = vo.getFoodmake().split("\n");
for (String s : makes) {
    StringTokenizer st = new StringTokenizer(s, "^");
    mList.add(st.nextToken());   // 조리 순서 설명
    iList.add(st.nextToken());   // 그 단계의 이미지 경로
}
```

```html
<table th:each="m,stat:${mList}">
  <tr>
    <td>[[${m}]]</td>
    <td><img th:src="${iList[stat.index]}"></td>
  </tr>
</table>
```

- CLOB 컬럼(`foodmake`) 안에 줄바꿈(`\n`)으로 각 조리단계가, `^` 기호로 "설명^이미지경로"가 함께 저장되어 있어서 `StringTokenizer`로 두 값을 분리해 각각 `mList`(설명), `iList`(이미지)에 담음
- `th:each="m,stat:${mList}"`처럼 반복 변수 뒤에 콤마로 상태 변수(`stat`)를 추가하면 `stat.index`(현재 순번)를 쓸 수 있어서, **다른 리스트(`iList`)의 같은 순번 값**을 `${iList[stat.index]}`로 동기화해서 꺼내올 수 있음

---

**▶ 서버(Thymeleaf) 값을 Vue 앱 초기 데이터로 그대로 넘기기**

```html
<script>
  const chef_name = '[[${chef}]]'   // Thymeleaf가 렌더링 시점에 값을 채워넣음
</script>
```

```js
data() {
  return {
    chef: chef_name,   // 서버에서 내려준 값으로 Vue data 초기화
    ...
  }
},
methods: {
  async dataRecv() {
    await axios.get('.../recipe/recipe_chef_vue', {
      params: { page: this.curpage, chef: this.chef }
    })...
  }
}
```

- 컨트롤러가 `model.addAttribute("chef", chef)`로 넘긴 값을, Thymeleaf가 화면을 그릴 때 `[[${chef}]]`로 순수 문자열로 치환해서 `<script>` 안에 심어둠
- Vue 앱이 시작될 때 이 값을 `data()`의 초기값으로 사용 → 서버 렌더링(SSR 느낌의 초깃값)과 클라이언트 SPA(Vue axios 통신)를 한 화면 안에서 같이 쓰는 하이브리드 패턴
