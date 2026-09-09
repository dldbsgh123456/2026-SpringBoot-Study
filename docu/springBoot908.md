재료 기반 레시피 추천 (Vector 검색 + 재료 매칭)

**▶ 전체 흐름 - 3일차(임베딩 저장)에 이어지는 "검색" 단계**

```
브라우저 (재료 선택 UI, 바닐라 JS)
      │  POST /recipe/recommand  { ingredients: ["김치","돼지고기","두부"] }
      ▼
RecipeController (@ResponseBody)
      │
      ▼
RecipeService.recommandRecipes(ingredients)
      │ 1) 재료 목록 → 검색 문장 생성
      │ 2) EmbeddingModel로 문장 → 벡터 변환
      │ 3) PostgreSQL + pgVector 코사인 유사도 검색 (LIMIT 5)
      │ 4) 각 레시피의 content에서 재료/이름/조리법 등 추출
      │ 5) 사용자 보유 재료 vs 레시피 재료 비교 → 충족률 계산
      ▼
JSON 응답 (recipes: [...])
      ▼
브라우저 - 레시피 카드 렌더링
```

- 3일차에서 만들어둔 `recipe_vector`(임베딩 저장 테이블)를 이번엔 **검색 대상**으로 사용 — "저장"과 "검색" 양쪽 모두 **같은 임베딩 모델**을 써야 벡터 공간이 일치해서 유사도 비교가 의미 있음(주석에 명시된 중요 규칙)

---

**▶ 선택한 재료 배열을 하나의 검색 문장으로 변환**

```java
private String createQueryText(List<String> ingredients) {
    StringBuilder sb = new StringBuilder();
    sb.append("냉장고에 있는 재료를 이용할 수 있는 레시피. ");
    sb.append("사용 가능한 재료: ");
    for (String ingredient : ingredients) {
        sb.append(ingredient.trim()).append(" ");
    }
    return sb.toString().trim();
}
// 예: ["김치","돼지고기","두부"] → "냉장고에 있는 재료를 이용할 수 있는 레시피. 사용 가능한 재료: 김치 돼지고기 두부"
```

- 배열을 그대로 벡터로 바꾸지 않고, **자연어 문장 형태로 조립한 뒤** 임베딩 — 3일차에 레시피를 저장할 때도 "레시피명: ~, 주재료: ~" 식의 문장으로 만들었던 것과 같은 원칙(임베딩 모델은 문맥이 있는 문장에서 더 정확한 벡터를 만듦)

---

**▶ pgVector 코사인 유사도 검색 SQL**

```xml
<select id="findSimilarRecipes" resultType="hashmap">
  SELECT id, recipe_id, content,
   (embedding &lt;=&gt; CAST(#{embedding} AS vector)) AS distance,
   ( 1 - (embedding &lt;=&gt; CAST(#{embedding} AS vector)) ) AS similarity
  FROM recipe_vector
  ORDER BY embedding &lt;=&gt; CAST(#{embedding} AS vector)
  LIMIT #{limit}
</select>
```

- `<->`, `<=>`, `<#>`는 pgVector가 제공하는 **거리 연산자**들인데, 여기 쓰인 `<=>`는 **코사인 거리(cosine distance)** — 값이 작을수록(0에 가까울수록) 두 벡터가 유사함
- `CAST(#{embedding} AS vector)`: 파라미터로 넘긴 문자열(`"[0.1,0.2,...]"`)을 PostgreSQL의 `vector` 타입으로 명시적 캐스팅해야 연산자가 동작함
- `similarity = 1 - distance`: 코사인 거리는 "다를수록 큰 값"이라 직관과 반대라서, `1 - distance`로 **"유사할수록 큰 값(0~1)"인 유사도**로 바꿔서 화면에 퍼센트로 보여주기 좋게 가공
- `ORDER BY ... LIMIT #{limit}`: 거리가 가장 작은(가장 유사한) 순으로 정렬해서 상위 5개만 추출 — 이전에 다룬 "맛집 거리순 정렬"과 같은 패턴을 벡터 거리에 적용한 것

---

**▶ `content`(레시피 통합 텍스트)에서 필요한 값만 다시 뽑아내기**

3일차에 저장한 `content`는 "레시피명: ~\n조리방법: ~\n..." 형태의 통짜 텍스트라서, 화면에 항목별로 보여주려면 다시 파싱이 필요함:

```java
private String extractValue(String content, String label) {
    // "레시피명: 버섯 두유 소스 볶음" 형태에서 label 뒤의 값만 잘라냄
}

private List<String> extractIngredients(String content) {
    Set<String> ingredients = new HashSet<>();
    String[] knownIngredients = { "김치", "돼지고기", "소고기", ... };  // 알려진 재료 키워드 목록
    for (String ingredient : knownIngredients) {
        if (content.contains(ingredient)) {
            ingredients.add(ingredient);
        }
    }
    return new ArrayList<>(ingredients);
}
```

- `extractIngredients`는 형태소 분석 같은 정교한 방식이 아니라, **미리 정해둔 재료 키워드 목록과 단순 `contains` 매칭**으로 처리 — 구현이 간단하지만 목록에 없는 재료는 인식 못 하는 한계가 있음(주석에도 "실제 재료 목록을 이 배열에 추가하면 된다"고 명시)
- `extractCookingSteps`: `content`를 문장 단위(`(?<=[.!?])\s+` 정규식)로 쪼갠 뒤, "레시피명:", "영양정보:" 같은 메타정보 라인은 제외하고 **진짜 조리 설명처럼 보이는 문장만** 최대 6개까지 추림 — 데이터가 구조화되어 있지 않은 상태에서 텍스트 휴리스틱으로 콘텐츠를 뽑아내는 임시방편

---

**▶ 재료 충족률 계산 - 정규화 + 부분일치**

```java
private String normalizeIngredient(String ingredient) {
    return ingredient
            .replaceAll("[0-9]+(\\.\\d+)?", "")              // 숫자 제거 (예: "돼지고기 200g" → "돼지고기 g")
            .replaceAll("(g|kg|ml|L|개|모|대|큰술|작은술|컵|쪽|약간)", "") // 단위 제거
            .replaceAll("\\([^)]*\\)", "")                     // 괄호 설명 제거
            .trim()
            .toLowerCase();
}
```

- 레시피 재료는 보통 `"돼지고기 200g"`, `"대파 1대(약간)"`처럼 **수량/단위/부가설명이 붙어있어서**, 사용자가 고른 단순 재료명(`"돼지고기"`)과 문자열이 그대로는 절대 일치하지 않음 → 숫자·단위·괄호를 다 지워서 순수 재료명만 남기는 정규화 과정이 필요

```java
if (normalized.contains(userIngredient) || userIngredient.contains(normalized)) {
    exists = true;
}
```

- 정확히 같은 문자열이 아니라 **서로 포함 관계면 일치로 인정** — `"새송이버섯"`(레시피 재료)과 `"버섯"`(사용자가 고른 재료)처럼, 세부 품종까지 정확히 안 맞아도 넓게 매칭되게 함

```java
double rate = ((double) have.size() / recipeIngredients.size()) * 100.0;
rate = Math.round(rate * 10.0) / 10.0;   // 소수점 1자리로 반올림
```

- 충족률 = (보유한 재료 수 ÷ 레시피가 필요로 하는 전체 재료 수) × 100

---

**▶ 프론트엔드 - XSS 방지 + 동적 카드 렌더링**

```js
function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
```

- 서버에서 받은 레시피 텍스트를 `template literal`로 그대로 HTML에 꽂아 넣기 전에 **모든 값을 `escapeHtml`로 한 번씩 감싸서** 출력 — 레시피 데이터(DB) 안에 우연히 `<script>` 같은 문자열이 섞여 있어도 실제 태그로 실행되지 않고 그냥 문자로 표시되게 막는 처리
- 보유 재료는 `✅`, 부족 재료는 `❌` 아이콘을 붙여서 태그(`<span class="tag">`) 형태로 나열하고, `PGVector 유사도`와 `재료 충족률` 두 지표를 카드 상단에 같이 보여줌 — **AI 유사도(의미가 얼마나 비슷한가)**와 **실제 재료 보유율(현실적으로 만들 수 있는가)**을 분리해서 제공하는 게 이 추천 기능의 핵심 설계

---

**▶ 컨트롤러 주석에 남겨진 다음 계획**

```
1. JavaScript(바닐라) → Pinia로 전환
2. @ResponseBody 방식 → @RestController로 정리
3. @Tool (Tool Calling) - 프롬프트 기반 검색으로 확장
4. 기능별 분리 → MCP
```

- 지금은 `@Controller` + `@ResponseBody`로 화면과 API를 한 클래스에서 처리하고 있지만, 이후 REST 전용 컨트롤러 분리, LLM의 Tool Calling(함수 호출) 적용, MCP(기능별 서버 분리)까지 확장할 계획이 주석으로 남아있음
