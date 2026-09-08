# 3일차 - Spring AI Embedding으로 레시피 벡터화

**▶ 전체 흐름**

```
Oracle (recipe 원본)
      │
      ▼
  텍스트로 조립 (createContent)
      │
      ▼
Spring AI EmbeddingModel.embed() → float[] 벡터
      │
      ▼
문자열로 변환 ("[0.01,0.02,...]")
      │
      ▼
PostgreSQL recipe_vector 테이블에 저장
      │
      ▼
(이후) 유사 레시피 검색 → 부족한 재료 계산 → Gemini로 최종 레시피 생성
```

- 지금까지(1~2일차)는 데이터를 그대로 옮기기만 했다면, 이번엔 레시피 내용을 **AI가 의미 기반으로 검색할 수 있는 벡터(숫자 배열)로 변환**해서 저장하는 단계 — RAG(검색 증강 생성) 준비 단계

---

**▶ 레시피 정보를 검색용 텍스트로 조립**

```java
private String createContent(RecipeVO vo) {
    return """
            레시피명: %s
            조리방법: %s
            요리종류: %s
            영양정보: %s kcal
            탄수화물: %s
            단백질: %s
            지방: %s
            나트륨: %s
            해시태그: %s
            주재료: %s
            조리정보: %s
            요리팁: %s
           """.formatted(vo.getRcp_nm(), vo.getRcp_way2(), vo.getRcp_pat2(),
                          vo.getInfo_eng(), vo.getInfo_car(), vo.getInfo_pro(),
                          vo.getInfo_fat(), vo.getInfo_na(), vo.getHash_tag(),
                          vo.getRcp_parts_dtls(), vo.getAtt_file_no_mk(), vo.getRcp_na_tip());
}
```

- 여러 컬럼에 흩어진 값을 **하나의 자연어 문단**으로 합침 — 임베딩 모델은 이렇게 사람이 읽는 텍스트를 입력받아 의미를 벡터로 변환하기 때문에, 컬럼 값을 그냥 나열하는 것보다 "레시피명: ~, 조리방법: ~"처럼 **라벨을 붙여 문맥을 명확히** 해주는 게 검색 품질에 유리
- Java Text Block(`"""`)의 `.formatted(...)`로 `%s` 자리에 값을 순서대로 채워 넣음

**▶ Spring AI `EmbeddingModel`로 벡터 생성**

```java
@Service
@RequiredArgsConstructor
public class RecipeVectorService {
    private final EmbeddingModel model;   // Spring AI가 제공하는 임베딩 모델 클라이언트

    public void recipeVectorInsert() {
        List<RecipeVO> list = oMapper.oracleRecipeAllData();
        for (RecipeVO recipe : list) {
            String content = createContent(recipe);
            float[] vector = model.embed(content);              // 텍스트 → 숫자 벡터
            String embedding = convertVector(vector);             // 벡터 → 문자열
            RecipeVectorVO vo = RecipeVectorVO.builder()
                    .recipe_id((long) recipe.getRcp_seq())
                    .content(content)
                    .embedding(embedding)
                    .build();
            pMapper.recipeVectorInsert(vo);
        }
    }

    private String convertVector(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();   // PostgreSQL의 vector 타입이 인식하는 "[0.1,0.2,...]" 형식
    }
}
```

- `model.embed(content)`: 문자열을 넣으면 지정된 임베딩 모델(`gemini-embedding-001`)이 **고정 차원(여기선 768차원)의 실수 배열**로 변환해서 반환 — 의미가 비슷한 문장일수록 벡터 값도 가까워짐(코사인 유사도 등으로 "유사한 레시피"를 계산할 수 있게 됨)
- `float[]`를 PostgreSQL의 `vector` 타입 컬럼에 저장하려면 **텍스트 형식(`[0.01,0.02,...]`)으로 직접 변환**해서 문자열로 넘겨야 함 — JDBC 드라이버가 자바 배열을 자동으로 vector 타입에 매핑해주지 않기 때문에 이런 수동 변환 단계가 필요

**▶ `application.yml` - Spring AI(Google GenAI) 임베딩 설정**

```yaml
spring:
  ai:
    google:
      genai:
        api-key: ${GEMINI_KEY}
        project-id: gen-lang-client-0074645742
        location: us-central1
        embedding:
          api-key: ${GEMINI_KEY}
          project-id: gen-lang-client-0074645742
          text:
            options:
              model: gemini-embedding-001
              dimensions: 768
    model:
      embedding:
        text: google-genai
```

- API 키는 `${GEMINI_KEY}`로 환경변수에서 읽어옴 — 코드/yml에 키를 직접 노출하지 않는 패턴(앞서 DB 접속정보에 썼던 것과 동일한 방식)
- `dimensions: 768`: 이 프로젝트에서 사용할 임베딩 벡터의 차원 수 — PostgreSQL `recipe_vector` 테이블의 `embedding` 컬럼도 이 차원 수(768)에 맞게 `vector(768)`로 정의되어 있어야 함

**▶ `RecipeVectorVO` - 벡터 데이터 저장용 VO**

```java
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RecipeVectorVO {
    private Long id;
    private Long recipe_id;   // 원본 Oracle recipe의 rcp_seq를 참조
    private String content;    // 임베딩에 사용한 원본 텍스트 (나중에 결과 확인/디버깅용)
    private String embedding;  // 벡터를 문자열로 표현한 값
}
```

- `content`를 벡터와 함께 저장해두면, 나중에 "이 벡터가 어떤 텍스트에서 나온 건지" 사람이 확인하거나 검색 결과를 보여줄 때 바로 활용 가능
- `recipe_id`로 원본 Oracle 레시피(`rcp_seq`)와 연결 — 벡터 검색으로 유사한 항목을 찾은 뒤, 이 id로 원본 상세 정보를 다시 조회하는 흐름으로 이어짐

**▶ 이후 이어질 단계 (주석에 명시된 전체 그림)**

```
recipe_vector에서 유사 레시피 검색
        │
부족한 재료 계산
        │
Gemini로 최종 레시피 생성
```

- 지금 구현한 부분은 "임베딩해서 저장하기"까지 — 실제 유사도 검색(pgVector의 벡터 검색 쿼리)과 Gemini를 이용한 생성형 응답은 다음 단계에서 이어질 예정
