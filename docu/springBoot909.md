# 재료 선택 UI

**▶ 재료 클릭 토글 (선택/해제)**

```js
let selectedIngredients = [];

document.querySelectorAll(".ingredient").forEach(function(item) {
    item.addEventListener("click", function() {
        const name = item.dataset.name;   // data-name="돼지고기" 속성값

        if (selectedIngredients.includes(name)) {
            // 이미 선택되어 있으면 배열에서 제거
            selectedIngredients = selectedIngredients.filter(v => v !== name);
            item.classList.remove("selected");
        } else {
            // 선택 안 되어 있으면 추가
            selectedIngredients.push(name);
            item.classList.add("selected");
        }

        updateSelectedList();
    });
});
```

- 각 재료 칸(`<div class="ingredient" data-name="돼지고기">`)에 `data-name` 속성으로 재료명을 심어두고, 클릭 시 `dataset.name`으로 꺼내 씀 — HTML 속성에 값을 담아 JS와 연결하는 기본 패턴
- 배열에서 항목을 지울 때는 (지난번에 짚었던) `splice`가 아니라 **`filter`로 새 배열을 만들어 재할당**하는 방식 사용 — 원본을 직접 수정하지 않고 "조건에 맞는 것만 남긴 새 배열"로 통째로 교체하는 접근이라 `slice`/`splice` 혼동 문제 자체가 없음

**▶ 선택 목록 화면 갱신 (개수 표시 + 태그 렌더링 + 개별 삭제)**

```js
function updateSelectedList() {
    const list = document.getElementById("selectedList");
    const count = document.getElementById("selectedCount");

    count.textContent = selectedIngredients.length + "개 선택";
    list.innerHTML = "";

    if (selectedIngredients.length === 0) {
        list.innerHTML = '<span style="color:#999;font-size:13px;">재료를 선택해주세요.</span>';
        return;
    }

    selectedIngredients.forEach(function(name) {
        const item = document.createElement("span");
        item.className = "selected-item";
        item.textContent = name + " ×";
        item.style.cursor = "pointer";

        // 선택 태그를 클릭하면 그 재료만 선택 해제
        item.addEventListener("click", function() {
            selectedIngredients = selectedIngredients.filter(v => v !== name);

            document.querySelectorAll(".ingredient").forEach(function(ingredient) {
                if (ingredient.dataset.name === name) {
                    ingredient.classList.remove("selected");
                }
            });

            updateSelectedList();
        });

        list.appendChild(item);
    });
}
```

- 재료 그리드에서 선택 해제하는 것과, 상단 "선택한 재료" 태그를 클릭해서 해제하는 것 **두 진입점 모두 같은 상태(`selectedIngredients`)와 같은 갱신 함수(`updateSelectedList`)를 공유** — 상태가 어디서 바뀌든 화면 두 군데(그리드의 `selected` 클래스, 선택 목록 태그)가 항상 같이 갱신되도록 일관성 유지

**▶ 전체 삭제**

```js
document.getElementById("deleteAll").addEventListener("click", function() {
    selectedIngredients = [];
    document.querySelectorAll(".ingredient.selected").forEach(item => item.classList.remove("selected"));
    updateSelectedList();
});
```

---

**▶ 카테고리 필터 (버튼 클릭 시 재료 그리드 보이기/숨기기)**

```js
document.querySelectorAll(".category").forEach(function(button) {
    button.addEventListener("click", function() {
        // active 표시를 클릭한 버튼으로 옮김
        document.querySelectorAll(".category").forEach(btn => btn.classList.remove("active"));
        button.classList.add("active");

        const category = button.dataset.category;   // data-category="meat" 등

        document.querySelectorAll(".ingredient").forEach(function(item) {
            const match = (category === "all" || item.dataset.category === category);
            item.style.display = match ? "flex" : "none";
        });
    });
});
```

- 서버에 다시 요청하지 않고, **이미 화면에 그려진 재료 요소들을 `display` 속성으로 보이거나 숨기는 것만으로** 필터링 — 데이터가 적을 때 가장 간단하고 빠른 클라이언트 필터링 방식

**▶ 검색어 입력 필터**

```js
document.getElementById("ingredientSearch").addEventListener("input", function() {
    const keyword = this.value.trim().toLowerCase();

    document.querySelectorAll(".ingredient").forEach(function(item) {
        const name = item.dataset.name.toLowerCase();
        item.style.display = name.includes(keyword) ? "flex" : "none";
    });
});
```

- `input` 이벤트: 글자를 입력할 때마다(엔터 안 눌러도) 즉시 실행 — 실시간 검색(타이핑하는 즉시 필터링)에 적합
- 카테고리 필터와 검색 필터가 **같은 `.ingredient` 요소의 `display`를 각자 따로 건드리는 구조**라, 두 필터를 동시에 켜면 서로 덮어써서 의도한 대로 "AND 조건"으로 안 걸리는 점은 유의(카테고리로 걸러진 상태에서 검색하면 검색 로직이 카테고리 조건을 무시하고 전체 재료를 대상으로 다시 필터링함)
