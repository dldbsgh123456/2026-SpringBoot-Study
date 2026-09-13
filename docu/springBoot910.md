# AJAX 요청 & 결과 렌더링

**▶ 버튼 클릭 → 서버 요청 (`fetch`)**

```js
async function recommandRecipe() {
    if (selectedIngredients.length === 0) {
        alert("재료를 한 개 이상 선택해주세요.");
        return;
    }

    const button = document.getElementById("recommandButton");
    const loading = document.getElementById("loading");
    const resultArea = document.getElementById("resultArea");

    button.disabled = true;          // 중복 클릭 방지
    loading.style.display = "block"; // 로딩 문구 표시
    resultArea.innerHTML = "";

    try {
        const response = await fetch("/recipe/recommand", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ingredients: selectedIngredients })
        });

        const data = await response.json();

        if (!data.success) {
            resultArea.innerHTML = `<div class="empty">⚠️ ${escapeHtml(data.message || "검색에 실패했습니다.")}</div>`;
            return;
        }

        const recipes = data.recipes || [];
        if (recipes.length === 0) {
            resultArea.innerHTML = `<div class="empty">😢<br><br>유사한 레시피를 찾지 못했습니다.</div>`;
            return;
        }

        resultArea.innerHTML = recipes.map((recipe, index) => createRecipeHtml(recipe, index)).join("");

    } catch (error) {
        console.error(error);
        resultArea.innerHTML = `<div class="empty">❌ 서버와 통신할 수 없습니다.<br><br>Spring Boot 서버가 실행 중인지 확인해주세요.</div>`;
    } finally {
        loading.style.display = "none";
        button.disabled = false;
    }
}
```

- **버튼 비활성화(`disabled = true`) → `finally`에서 다시 활성화**: 응답이 오기 전에 버튼을 여러 번 눌러서 요청이 중복으로 나가는 걸 방지 — 성공/실패/에러 어떤 경로로 끝나든 `finally`가 항상 실행되므로 버튼이 계속 비활성 상태로 남는 일이 없음
- **응답을 세 단계로 구분해서 처리**: ① 네트워크 자체가 실패(서버 다운 등, `catch`) ② 서버는 응답했지만 `success: false`(재료 없음, 검색 오류) ③ 성공했지만 결과가 0건 — 각각 다른 안내 메시지를 보여줌
- `data.recipes || []`: 응답에 `recipes` 필드가 없거나 `null`이어도 `.map()` 호출 시 에러가 안 나도록 빈 배열로 안전하게 처리(디펜시브 코딩)

---

**▶ 레시피 하나를 카드 HTML로 변환**

```js
function createRecipeHtml(recipe, index) {
    // similarity: 0.923 형태(0~1) → 퍼센트로 변환
    let similarity = Number(recipe.similarity || 0);
    let similarityPercent = Math.round(similarity * 100 * 10) / 10;   // 소수점 1자리

    let ingredientRate = Number(recipe.ingredientRate || 0);
    const have = recipe.haveIngredients || [];
    const missing = recipe.missingIngredients || [];
    const steps = recipe.steps || [];

    // 이미지가 없거나 URL 형태가 아니면 기본 이미지로 대체
    let image = recipe.recipeImage;
    if (!image || !image.startsWith("http")) {
        image = "https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&w=900&q=80";
    }

    const recipeName = recipe.recipeName || "추천 레시피";

    // 보유/부족 재료를 각각 태그(span) 문자열로 변환
    const haveHtml = have.length > 0
        ? have.map(v => `<span class="tag">✅ ${escapeHtml(v)}</span>`).join("")
        : `<span class="tag">없음</span>`;

    const missingHtml = missing.length > 0
        ? missing.map(v => `<span class="tag">❌ ${escapeHtml(v)}</span>`).join("")
        : `<span class="tag">🎉 모든 재료 보유</span>`;

    // 조리 과정 - 순번을 붙여서 나열, 없으면 안내문구 하나 표시
    let stepsHtml = steps.length > 0
        ? steps.map((step, i) => `
            <div class="step">
                <span class="step-number">${i + 1}</span>
                <span>${escapeHtml(step)}</span>
            </div>`).join("")
        : `<div class="step"><span class="step-number">1</span><span>레시피 상세 화면에서 조리 방법을 확인해주세요.</span></div>`;

    return `
        <div class="recipe-result">
          <div class="recipe-header">
            <div>
              <div class="recipe-name"><span class="recipe-icon">🍲</span> ${escapeHtml(recipeName)}</div>
              <div class="scores">
                <div class="score orange">PGVector 유사도 <strong>${similarityPercent}%</strong></div>
                <div class="score green">재료 충족률 <strong>${ingredientRate}%</strong></div>
              </div>
            </div>
            <img class="recipe-image" src="${escapeHtml(image)}" alt="${escapeHtml(recipeName)}"
                 onerror="this.src='https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&w=900&q=80'">
          </div>
          <div class="status">
            <div class="status-box have"><div class="status-title">✅ 가지고 있는 재료</div><div class="tags">${haveHtml}</div></div>
            <div class="status-box missing"><div class="status-title">❌ 부족한 재료</div><div class="tags">${missingHtml}</div></div>
          </div>
          <div class="final-recipe">
            <div class="final-title">👨‍🍳 최종 레시피</div>
            <div class="recipe-content">
              <div class="ingredients">
                <h3>🛒 재료</h3>
                <ul>
                  ${have.map(v => `<li>${escapeHtml(v)}</li>`).join("")}
                  ${missing.map(v => `<li>${escapeHtml(v)} <strong style="color:red;">(부족)</strong></li>`).join("")}
                </ul>
              </div>
              <div class="steps"><h3>👩‍🍳 조리 방법</h3>${stepsHtml}</div>
            </div>
            <div class="tip">
              <div class="tip-title">💡 AI 추천 TIP</div>
              현재 냉장고 재료와 가장 유사한 레시피를 PostgreSQL pgVector를 이용해 검색했습니다.
              ${missing.length > 0 ? " 부족한 재료를 준비하면 더욱 정확하게 조리할 수 있습니다." : " 현재 보유한 재료만으로 조리할 수 있는 레시피입니다."}
            </div>
          </div>
        </div>
    `;
}
```

- 서버가 내려준 `similarity`(0~1 소수)를 `× 100` 해서 사람이 읽기 쉬운 퍼센트로 변환 — 이 변환은 서버가 아니라 **화면 표시 직전(프론트)**에서 함, 서버 응답 자체는 원본 소수값 그대로 유지
- `have`/`missing` 배열이 비어있을 때를 각각 "없음" / "🎉 모든 재료 보유"로 다르게 문구를 처리 — 단순히 빈 태그를 보여주지 않고 상황에 맞는 메시지를 보여줌
- `onerror="this.src='...'"`: `recipeImage`로 받은 URL이 실제로는 깨진 링크일 경우, 이미지 로드 실패 이벤트에서 **한 번 더** 기본 이미지로 교체 — `!image.startsWith("http")` 체크만으로는 "http로 시작하지만 실제로 존재하지 않는 URL"까지는 못 걸러내므로 이 이중 방어가 필요

**▶ 5A(선택 UI)와 5B(검색/렌더링)의 관계**

- 5A에서 관리하는 `selectedIngredients` 배열을 5B의 `recommandRecipe()`가 그대로 읽어서 서버로 전송 — 두 파일의 로직이 **하나의 전역 변수(`selectedIngredients`)를 공유**하는 구조라서, 실제로는 한 `<script>` 안에 순서대로 이어져 있어야 정상 동작함(따로 파일을 분리한다면 모듈화/상태 공유 방식을 별도로 고려해야 함)
