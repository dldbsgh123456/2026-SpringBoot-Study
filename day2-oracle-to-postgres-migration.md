# 2일차 - Oracle → PostgreSQL 데이터 마이그레이션

**▶ 목표 - 한쪽 DB에서 읽어서 다른 쪽 DB에 그대로 저장**

```java
@Service
@RequiredArgsConstructor
public class RecipeService {
    private final OracleRecipeMapper oMapper;
    private final PostgresRecipeMapper pMapper;

    public void recipeInsert() {
        List<RecipeVO> list = oMapper.oracleRecipeAllData();  // Oracle에서 전체 조회
        for (RecipeVO vo : list) {
            pMapper.postgresRecipeInsert(vo);                  // PostgreSQL에 한 건씩 저장
        }
    }
}
```

- 하나의 Service 안에서 **서로 다른 DB의 Mapper 두 개를 동시에 주입**받아 사용 — 1일차에서 분리해둔 `oracleSqlSessionFactory`/`postgresSqlSessionFactory` 덕분에 각 Mapper 호출이 알아서 맞는 DB로 나감
- 트랜잭션 관점에서는 두 DB가 물리적으로 다르기 때문에 **하나의 `@Transactional`로 두 DB를 한 번에 롤백**시키기는 어려움(분산 트랜잭션 필요) — 이 코드는 그런 처리 없이 단순 반복 삽입만 수행

```java
@RestController
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService rs;

    @GetMapping("/recipe")
    public String recipe_insert() {
        rs.recipeInsert();
        return "데이터 저장 완료";
    }
}
```

- `/recipe`로 GET 요청 한 번이면 Oracle 전체 데이터를 읽어서 PostgreSQL로 옮기는 배치성 작업이 실행됨 (실무에서는 이런 대량 작업을 GET 하나로 트리거하는 건 지양하고, 배치 잡/스케줄러로 분리하는 게 일반적)

---

**▶ 같은 이름의 매퍼가 3벌 존재하는 이유**

| 매퍼 | 위치 | 연결 DB | 역할 |
|---|---|---|---|
| `RecipeMapper` | `mapper` (공통 패키지) | 기본 DataSource | 예전/공통 조회용 (`@Select` 어노테이션 방식) |
| `OracleRecipeMapper` | `mapper.oracle` | `oracleSqlSessionFactory` | Oracle 원본 데이터 조회 전용 |
| `PostgresRecipeMapper` | `mapper.postgre` | `postgresSqlSessionFactory` | PostgreSQL 저장(INSERT) 전용 |

- 이름은 비슷해도 **패키지가 다르면 `@MapperScan`에 의해 완전히 다른 DB로 라우팅**되기 때문에, 이름 하나만 보고 헷갈리지 않게 패키지 구조로 명확히 구분해두는 것이 중요

**▶ `RecipeVO` - Oracle 원본 테이블 구조를 그대로 반영**

```java
@Data
public class RecipeVO {
    private int rcp_seq, hit;
    private double info_eng, info_car, info_pro, info_fat, info_na;
    private String rcp_nm, rcp_way2, rcp_pat2, hash_tag,
                   att_file_no_main, att_file_no_mk,
                   rcp_parts_dtls, rcp_na_tip, user_id, info_wgt;
}
```

- 필드명을 Oracle 컬럼명(`RCP_SEQ`, `RCP_NM` 등)의 소문자 스네이크케이스 그대로 사용 — `map-underscore-to-camel-case: true` 설정이 있긴 하지만, 여기서는 필드 자체를 원본 컬럼명과 맞춰서 Oracle/PostgreSQL 양쪽 매퍼에서 동일한 VO를 그대로 재사용할 수 있게 함
