# 1일차 - 다중 데이터소스(Oracle + PostgreSQL) 설정

**▶ 왜 DB를 두 개 동시에 연결하나**

- Oracle에는 기존 레시피 원본 데이터가 있고, PostgreSQL(pgVector 확장 지원)에는 벡터 검색용 데이터를 저장 — **원본 DB와 벡터 DB를 분리**해서 운영하는 구조
- 하나의 Spring Boot 애플리케이션 안에서 서로 다른 두 DB에 각각 연결하고, 각 DB 전용 MyBatis 매퍼를 따로 스캔하도록 설정해야 함

---

**▶ `application.yml` - DB별 커스텀 프로퍼티**

```yaml
spring:
  datasource:
    postgres:
      jdbc-url: jdbc:postgresql://localhost:5432/recipe
      username: postgres
      password: "1234"
      driver-class-name: org.postgresql.Driver
    oracle:
      jdbc-url: jdbc:oracle:thin:@211.238.142.45:1521:XE
      username: hr
      password: happy
      driver-class-name: oracle.jdbc.driver.OracleDriver

mybatis:
  type-aliases-package: com.sist.web.vo
  mapper-locations:
    - classpath:/mapper/oracle/*.xml
    - classpath:/mapper/postgres/*.xml
  configuration:
    map-underscore-to-camel-case: true   # DB컬럼(snake_case) ↔ 자바 필드(camelCase) 자동 매핑
```

- Spring Boot가 기본 인식하는 `spring.datasource.url` 자리 대신, **`spring.datasource.postgres`, `spring.datasource.oracle`처럼 임의의 하위 경로**로 나눠서 설정 — 기본 자동 설정으로는 DB 하나만 잡히므로, 두 DB를 쓰려면 이렇게 커스텀 prefix로 각각 묶어두고 직접 Bean으로 등록해야 함
- `mapper-locations`를 리스트로 여러 경로(`oracle/*.xml`, `postgres/*.xml`) 지정 가능

---

**▶ `DataSourceConfig` - 커스텀 prefix를 각각 `DataSource` Bean으로 등록**

```java
@Configuration
public class DataSourceConfig {
    @Bean(name = "oracleDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.oracle")
    public DataSource oracleDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "postgresDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.postgres")
    public DataSource postresDataSource() {
        return DataSourceBuilder.create().build();
    }
}
```

- `@ConfigurationProperties(prefix="...")`: yml의 해당 경로 아래 값들(jdbc-url, username, password, driver-class-name)을 자동으로 읽어서 `DataSourceBuilder`에 채워줌
- `@Bean(name="...")`으로 이름을 명시적으로 지정 — 같은 타입(`DataSource`) Bean이 두 개 있어서, 이후 주입받을 때 이름으로 구분해야 함

---

**▶ DB별 `SqlSessionFactory` / `SqlSessionTemplate` 분리 설정**

```java
@Configuration
public class OracleMyBatisConfig {
    @Bean(name = "oracleSqlSessionFactory")
    public SqlSessionFactory oracleSqlSessionFactory(
            @Qualifier("oracleDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:/mapper/oracle/*.xml"));
        return factory.getObject();
    }

    @Bean(name = "oracleSessionTemplate")
    public SqlSessionTemplate oracleSessionTemplate(
            @Qualifier("oracleSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
```

- `@Qualifier("oracleDataSource")`: `DataSource` Bean이 여러 개라 타입만으로는 어떤 걸 주입할지 애매하므로, **이름으로 정확히 지정**해서 가져옴
- PostgreSQL 쪽도 완전히 동일한 구조로 `postgresSqlSessionFactory`/`postgresSessionTemplate`를 별도 클래스(`PostgresMyBatisConfig`)에 구성 — Oracle 매퍼는 `mapper/oracle/*.xml`만, Postgres 매퍼는 `mapper/postgres/*.xml`만 스캔하도록 경로 자체를 분리

**▶ `@MapperScan`으로 매퍼 인터페이스를 DB별 SqlSessionFactory에 연결**

```java
@Configuration
@MapperScan(basePackages = "com.sist.web.mapper.oracle",
            sqlSessionFactoryRef = "oracleSqlSessionFactory")
public class OracleMapperScanConfig { }

@Configuration
@MapperScan(basePackages = "com.sist.web.mapper.postgre",
            sqlSessionFactoryRef = "postgresSqlSessionFactory")
public class PostgresMapperScanConfig { }
```

- 매퍼 인터페이스도 패키지 자체를 `mapper.oracle` / `mapper.postgre`로 분리하고, `@MapperScan(sqlSessionFactoryRef=...)`로 **"이 패키지의 매퍼들은 이 SqlSessionFactory(=이 DB 연결)를 써라"**를 지정
- 결과적으로 `OracleRecipeMapper`를 호출하면 Oracle 커넥션으로, `PostgresRecipeMapper`를 호출하면 PostgreSQL 커넥션으로 각각 나가게 됨 — 하나의 `@Mapper` 어노테이션 방식으로는 이 분리가 안 되고, 반드시 이런 명시적 `SqlSessionFactory` 분리 설정이 필요
