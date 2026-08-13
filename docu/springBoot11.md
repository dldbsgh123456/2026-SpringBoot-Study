# 🍳 레시피 공유 프로젝트 — Frontend 구조 정리


## 🗂️ 전체 구조도

```
App.vue (HeaderCont + router-view)
 ├─ components/
 │   ├─ HeaderCont.vue   ─ 상단 네비게이션 바
 │   └─ HelloWorld.vue   ─ Vue CLI 기본 데모 컴포넌트 (미사용 추정)
 │
 ├─ views/
 │   ├─ HomeView.vue          레시피 목록 + 페이지네이션
 │   ├─ RecipeDetailView.vue  레시피 상세 (조리순서 · 셰프정보)
 │   ├─ RecipeFindView.vue    레시피 검색 + 페이지네이션
 │   └─ YoutubeView.vue       유튜브 API 검색
 │
 ├─ store/
 │   ├─ recipe.js  (Pinia)  ← 실제 사용 중인 상태관리
 │   └─ index.js   (Vuex)   ← 미사용 추정, 정리 대상
 │
 └─ router/
     └─ index.js   ─ 4개 라우트 정의
```

---

## 🧭 라우팅 (`router/index.js`)

| Path | Name | Component | 설명 |
|---|---|---|---|
| `/` | `home` | `HomeView` | 메인 레시피 목록 |
| `/recipe/detail/:no` | `recipe_detail` | `RecipeDetailView` | 레시피 상세 (번호 파라미터) |
| `/recipe/find` | `recipe_find` | `RecipeFindView` | 레시피 검색 |
| `/youtube/list` | `youtube` | `YoutubeView` | 유튜브 동영상 검색 |

`createWebHistory(process.env.BASE_URL)`로 히스토리 모드를 사용하므로, 배포 시 서버 쪽에서
새로고침 시 `index.html`로 fallback 처리하는 설정(예: nginx `try_files`)이 필요.

---

## 🗃️ 상태관리 — Pinia (`store/recipe.js`)

### State
```js
state: () => ({
  recipe_list: { list: [], pages: [], count: 0 },
  recipe_detail: { vo: {}, mList: [], iList: [] },
  find_list: { list: [], pages: [] }
})
```

| State | 용도 |
|---|---|
| `recipe_list` | 홈 화면 레시피 목록 데이터. `list`(레시피 배열), `pages`(페이지네이션 정보 배열), `count`(총 개수) |
| `recipe_detail` | 상세 화면 데이터. `vo`(레시피 기본정보), `mList`(조리순서 텍스트 배열), `iList`(조리순서별 이미지 배열) |
| `find_list` | 검색 결과 데이터. `list`, `pages` |

### Actions

| Action | 파라미터 | 호출 API | 비고 |
|---|---|---|---|
| `recipeListData(page)` | 페이지 번호 | `GET /recipe/list_vue` | 홈 화면 목록 조회 |
| `recipeDetailData(no)` | 레시피 번호 | `GET /recipe/detail_vue` | 상세 조회 |
| `recipeFindData(page, fd)` | 페이지 번호, 검색어 | `GET /recipe/find_vue` | 검색어 기반 조회 |

> ⚠️ **하드코딩 주의**: `http://localhost:8080`이 세 액션 모두에 개별적으로 박혀 있음.
> `axios.create({ baseURL: import.meta.env.VITE_API_BASE })` 형태로 인스턴스를 분리하면
> 배포 환경마다 baseURL만 바꿔주면 되므로 유지보수가 훨씬 쉬워짐.

### `pages` 배열 구조 (페이지네이션)
코드에서 `pages[0]`, `pages[1]`, `pages[2]`, `pages[3]`을 다음과 같이 사용하고 있는 것으로 추정:

| index | 추정 의미 |
|---|---|
| `pages[0]` | 현재 페이지 번호 |
| `pages[1]` | 전체 페이지 수 |
| `pages[2]` | 현재 블록의 시작 페이지 |
| `pages[3]` | 현재 블록의 끝 페이지 |

> ▶ 백엔드 컨트롤러에서 이 배열을 만드는 로직도 README에 같이 남겨두면,
> 나중에 프론트/백 어느 쪽만 봐도 페이지네이션 구조를 바로 이해할 수 있음.

---

## 🖥️ 컴포넌트별 상세

### `HeaderCont.vue` — 상단 네비게이션

```vue
<router-link to="/">Home</router-link>
<router-link to="/recipe/find">레시피 검색</router-link>
<a href="/recipe/chef_list">쉐프</a>
<a href="/databoard/list">자료실</a>
<router-link to="/youtube/list">동영상 검색</router-link>
```

- 드롭다운 메뉴(`레시피`) 안에 검색/쉐프 링크 포함
- **이슈**: `router-link`(SPA 라우팅)와 `<a href>`(풀 페이지 리로드)가 혼용됨.
  `/recipe/chef_list`, `/databoard/list`는 `router/index.js`에 등록되어 있지 않은 경로 → Vue 라우터가 아닌
  서버(Spring MVC) 쪽 페이지로 연결되는 것으로 보임. 프론트에 완전히 통합할 계획이면 라우트 추가 필요.
- **이슈**: `active` 클래스가 `Home` `<li>`에 하드코딩되어 있어, 다른 메뉴로 이동해도 Home이 계속 active로 표시됨.
  `$route.name`을 기준으로 동적 바인딩(`:class="{active: $route.name==='home'}"`) 처리 권장.

### `HomeView.vue` — 레시피 목록

- Pinia의 `recipe_list`를 `storeToRefs`로 반응형 참조
- `onMounted`에서 `recipeListData(1)` 호출 → 첫 페이지 로드
- `range(start, end)` 헬퍼로 페이지 번호 배열 생성 후 `v-for`로 페이지네이션 렌더링
- 각 레시피 카드는 `router-link`로 `recipe_detail` 라우트 연결 (정상)

```js
const range = (start, end) => {
  const arr = []
  const len = end - start
  for (let i = 0; i < len; i++) {
    arr[i] = start
    start++
  }
  return arr
}
```

> ▶ `range(start, end)`가 `end`를 포함하지 않는 구조(`len = end - start`)라서,
> `pages[3]`(블록 끝 페이지)까지 보여주려면 호출부에서 `end+1`을 넘기고 있는지 확인 필요.
> 현재 코드상 `range(recipe_list.pages[2], recipe_list.pages[3])`로 호출되는데,
> 끝 페이지가 목록에서 빠질 수 있는 지점이니 실제 렌더링 결과 확인 권장.

### `RecipeDetailView.vue` — 레시피 상세

- `useRoute()`로 `route.params.no`를 받아 `onMounted`에서 상세 조회
- 조리순서(`mList`)와 순서별 이미지(`iList`)를 인덱스로 매칭해서 렌더링:
  ```vue
  <table v-for="(m, index) in recipe_detail.mList" :key="index">
    <td>{{ m }}</td>
    <td><img :src="recipe_detail.iList[index]"></td>
  </table>
  ```
- 셰프 정보(사진/이름/프로필) 별도 테이블로 하단에 표시
- `<a href="javascript:history.back()">` 로 목록 복귀 — SPA 환경에서는 `router.back()`이 더 Vue다운 방식

### `RecipeFindView.vue` — 레시피 검색

- 검색창(`input` + `keydown.enter`) → `find()` 호출 → `recipeFindData(1, fd.value)`
- 페이지네이션 구조는 `HomeView`와 동일한 패턴

> ⚠️ **버그 의심**: 마지막 페이지(`&raquo;`) 버튼에서
> ```vue
> <a @click="move(find_list.pages[3]+1,fd)">&raquo;</a>
> ```
> `move`라는 함수를 호출하는데, `<script setup>` 안에는 `move`가 정의되어 있지 않고
> `recipeFindData`만 정의되어 있음. 클릭 시 콘솔 에러가 나거나 아무 동작도 하지 않을 가능성이 높음.
> → `recipeFindData(find_list.pages[3]+1, fd)`로 수정 필요.

> ⚠️ **기능 누락**: 검색 결과 카드가
> ```vue
> <a href="#"><div class="thumbnail">...</div></a>
> ```
> 로만 되어 있어서 클릭해도 상세 페이지로 이동하지 않음. `HomeView`처럼
> `router-link :to="{name:'recipe_detail', params:{no:vo.no}}"`로 감싸주면 일관성이 맞음.

### `YoutubeView.vue` — 유튜브 검색

- Pinia를 쓰지 않고 컴포넌트 로컬 `ref`(`title`, `youtubes`)로만 상태 관리
- `fetch`로 YouTube Data API v3 `search` 엔드포인트 직접 호출
- 검색어 기본값 `"서울여행"`, 최대 12개 결과, `type=video`로 제한

> 🚨 **보안 이슈 (우선순위 높음)**: API 키가 소스코드에 그대로 하드코딩되어 있음.
> ```js
> `https://youtube.googleapis.com/youtube/v3/search?...&key=AIzaSy...`
> ```
> 이 상태로 GitHub에 올리면 키가 그대로 공개됨. 다음 중 하나는 반드시 처리하고 올리는 걸 권장:
> 1. `.env` 파일에 `VITE_YOUTUBE_KEY`로 분리하고 `.gitignore`에 `.env` 추가
> 2. 이미 커밋된 이력이 있다면 GitHub에만 지우는 게 아니라 **Google Cloud Console에서 키 자체를 재발급**
>    (git history에 남아있으면 지워도 복구 가능)

### `HelloWorld.vue`, `store/index.js` (Vuex)

- 두 파일 모두 실제 라우트/컴포넌트 어디에서도 import되는 흔적이 없음
- `HelloWorld.vue`: `vue create` 시 자동 생성되는 튜토리얼용 컴포넌트
- `store/index.js`: Pinia로 전환하면서 남은 Vuex 잔재로 추정
- 정리하면서 같이 삭제하거나, 미사용이 확실치 않다면 일단 이슈로만 남겨두고 나중에 확인 권장

---

