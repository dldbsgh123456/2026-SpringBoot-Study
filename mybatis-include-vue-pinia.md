# MyBatis SQL 재사용 & Vue Pinia 상태관리 패턴

**▶ MyBatis `<sql>` + `<include>` - 반복되는 WHERE절 재사용**

```xml
<sql id="where-no">
 WHERE no IN(SELECT no FROM recipe2
               INTERSECT
               SELECT no FROM recipeDetail)
</sql>

<select id="recipeListData" resultType="com.sist.web.vo.RecipeVO" parameterType="int">
 SELECT no,poster,title,chef
 FROM recipe2
 <include refid="where-no"/>
 ORDER BY no DESC
 OFFSET #{start} ROWS FETCH NEXT 12 ROWS ONLY
</select>

<select id="recipeCount" resultType="int">
  SELECT COUNT(*)
  FROM recipe2
  <include refid="where-no"/>
</select>
```

- 목록 조회(`recipeListData`)와 총 개수 조회(`recipeCount`)가 **같은 조건절**을 써야 하는데, 조건을 두 군데에 복붙하는 대신 `<sql id="...">`로 한 번만 정의하고 `<include refid="...">`로 불러다 씀
- 조건이 바뀌면 `<sql>` 블록 한 곳만 고치면 이 조건을 쓰는 모든 쿼리에 반영됨

---

**▶ Thymeleaf에서 서버 값을 JS 변수로 안전하게 주입 (`th:inline="javascript"`)**

```html
<script th:inline="javascript">
const NO = /*[[${no}]]*/ 0
</script>
```

- 이전에 썼던 `const chef_name = '[[${chef}]]'` 방식은 문자열 전용이라 값이 없거나 이상하면 JS 문법이 깨질 위험이 있었음
- `th:inline="javascript"`를 스크립트 태그에 걸어두면, `/*[[${no}]]*/ 0`처럼 **주석 + 기본값** 형태로 써도 됨 → 편집기에서는 `const NO = 0`으로 정상 인식(문법 안 깨짐)되고, 실제 렌더링될 때는 주석 부분이 서버 값으로 치환됨
- 숫자형 값을 안전하게 JS로 넘길 때 특히 유용한 패턴

---

**▶ Vue 상태관리를 컴포넌트 밖(Pinia Store)으로 분리**

```js
// recipe/list.html
const { createApp, onMounted } = Vue
const { createPinia } = Pinia
const recipeApp = createApp({
    setup() {
        const store = useRecipeStore()      // 스토어 가져오기
        onMounted(() => {
            store.recipeListData()          // 마운트 시점에 목록 조회 요청
        })
        return { store }
    }
})
recipeApp.use(createPinia())
recipeApp.mount(".container")
```

```html
<!-- 템플릿에서 store.xxx로 바로 접근 -->
<div v-for="(vo,index) in store.list" :key="index">...</div>
<a class="a-link" @click="store.move(i)">{{i}}</a>
```

- 이전 버전들은 `data(){ return {list:[], curpage:1, ...} }`처럼 **각 화면(App)마다 상태와 axios 호출 로직을 따로** 두었는데, 여기서는 `recipeStore.js`(별도 파일)에 상태와 메소드를 몰아넣고, 화면은 `useRecipeStore()`로 그 store를 가져다 쓰기만 함
- `list.html`(목록)과 `detail.html`(상세) 둘 다 같은 `recipeStore`를 공유 — 상세 화면에서도 `store.detail`, `store.mList`, `store.iList`처럼 같은 스토어의 다른 상태를 사용
- 페이지네이션도 화면에서 직접 계산 안 하고 `store.range`, `store.startPage`, `store.move(i)`처럼 **스토어가 계산과 액션(axios 호출)까지 다 갖고 있고, 화면은 그 결과만 표시**하는 구조로 역할이 분리됨 (Vuex/Pinia의 전형적인 상태관리 패턴)

---

**▶ REST 컨트롤러를 별도 패키지로 분리**

```java
package com.sist.web.controller;      // 화면(View) 반환용
public class RecipeController { ... }

package com.sist.web.restcontroller;  // JSON 반환용
public class RecipeRestController { ... }
```

- 이전 프로젝트들은 `@Controller`와 `@RestController`가 같은 `controller` 패키지에 있었는데, 여기서는 `controller`(화면)와 `restcontroller`(JSON API)를 **패키지 자체로 분리**
- `RecipeController`는 이제 모델 데이터 없이 `return "recipe/list"`로 뷰 이름만 반환 — 실제 데이터는 화면 진입 후 Vue(Pinia store)가 `RecipeRestController`의 API를 호출해서 채우는 구조 (완전히 SPA 방식에 가까워짐)
