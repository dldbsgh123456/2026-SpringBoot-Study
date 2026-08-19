# Spring Boot - 파일 업로드 설정 & 3가지 업로드 UI

**▶ `application.yml`에 멀티파트 설정 추가**

```yaml
server:
  servlet:
    context-path: /
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
```

- `server.servlet.context-path` - 애플리케이션의 기본 URL 설정, 기본값이 `/`이긴 해서 없어도 괜찮음
- `spring.servlet.multipart.enabled: true` - 파일 업로드 처리 활성화, SpringBoot는 기본값이 true라 명시적으로 써 놓은 것
- `spring.servlet.multipart.max-file-size` - 업로드 파일 하나당 최대 100MB
- `spring.servlet.multipart.max-request-size` - 한 번의 요청(파일 여러 개 포함 가능)으로 최대 용량 100MB

---

**▶ `@SelectKey`로 채번하는 두 가지 방식**

```java
// 1) 시퀀스가 있는 경우 - NEXTVAL로 바로 채번
@SelectKey(keyProperty="no", resultType=int.class, before=true,
           statement="SELECT springdataboard_seq.NEXTVAL FROM dual")
@Insert("INSERT INTO springdataboard VALUES(#{no},#{name},#{subject},"
      +"#{content},#{pwd},SYSDATE,0,"
      +"#{filename},#{filesize},#{filecount})")
public void springdataboardInsert(DataBoardVO vo);
```

```java
// 2) 시퀀스가 없는 경우 - 현재 최댓값 + 1
@SelectKey(keyProperty="no", resultType=int.class, before=true,
           statement="SELECT NVL(MAX(no)+1,1) as no FROM springdataboard")
@Insert("INSERT INTO springdataboard VALUES("
      +"#{no},#{name},#{subject},"
      +"#{content},#{pwd},SYSDATE,0,"
      +"#{filename},#{filesize},#{filecount})")
public void databoardInsert(DataBoardVO vo);
```

- `@SelectKey(before=true)` - INSERT 실행 **전에** 지정한 SQL을 먼저 실행해서, 그 결과값을 `keyProperty`(여기선 `no`)에 채워 넣은 다음 INSERT
- 시퀀스 테이블이 있으면 `NEXTVAL`로 바로 채번(동시 요청에도 안전), 없으면 `NVL(MAX(no)+1,1)`로 직접 계산(동시 요청 시 중복 위험 있음 — 시퀀스가 있다면 그쪽이 더 안전)
- 이전 MyBatis 프로젝트에서는 Oracle 시퀀스(`sd_no_seq.nextval`)를 INSERT문 안에 직접 썼는데, 여기서는 `@SelectKey`로 **분리**해서 INSERT 전에 미리 번호를 받아옴 — VO(`vo.getNo()`)에서 생성된 번호를 바로 꺼내 쓸 수 있다는 차이가 있음

---

**▶ 실제 업로드 경로를 배포 환경에서 동적으로 구하기**

```java
String uploadDir = request.getServletContext().getRealPath("/upload");
File dir = new File(uploadDir);
if (!dir.exists()) {
    dir.mkdirs();   // 중첩 폴더까지 한 번에 생성
}
```

- 이전 프로젝트에서는 `String path = "c:\\upload"`처럼 경로를 하드코딩했는데, 여기서는 `HttpServletRequest.getServletContext().getRealPath(...)`로 **현재 배포된 서버 기준 실제 경로**를 구함 → 로컬/서버 어디에 배포되든 경로가 자동으로 맞춰짐
- 폴더가 없으면 `mkdirs()`로 생성 (`mkdir()`은 한 단계만 생성, `mkdirs()`는 중첩된 경로도 한 번에 생성)

**▶ `java.nio.file`로 파일 저장 (`transferTo` 대신 `Files.copy`)**

```java
Path path = Paths.get(uploadDir, f.getName());
Files.copy(file.getInputStream(), path);
```

- 이전엔 `MultipartFile.transferTo(File)`로 저장했는데, 여기서는 `Files.copy(InputStream, Path)`(java.nio) 방식을 사용
- `Paths.get(dir, name)`을 쓰면 운영체제별 경로 구분자(`\` vs `/`)를 신경 안 써도 됨

---

**▶ 업로드 UI 3가지 방식 비교**

| 방식 | 특징 |
|---|---|
| 기본 HTML form | `<input type=file multiple>` + `enctype="multipart/form-data"`로 폼 자체가 그대로 서버로 전송 |
| Vue + axios | `<input type=file>`의 `change` 이벤트로 파일을 JS 변수에 담고, `FormData`에 담아 axios로 POST |
| jQuery 동적 추가 | 버튼 클릭마다 `<input type=file>` 행을 동적으로 추가/삭제한 뒤, 폼 자체를 그대로 제출 |

```html
<!-- Vue 방식 -->
<input type="file" multiple @change="handlerFile">
<button @click="submit">업로드</button>
<script>
const app = Vue.createApp({
  data(){ return { files: [] } },
  methods: {
    handlerFile(e){ this.files = e.target.files },
    submit(){
      const formData = new FormData()
      for (let i of this.files) formData.append('files', i)
      axios.post('/multi-upload', formData, {
        headers: { 'Context-Type': 'multipart/form-data' }
      }).then(() => alert("등록완료!!"))
    }
  }
}).mount(".container")
</script>
```

```js
// jQuery 방식 - 파일 입력칸을 동적으로 추가/삭제
$('#addBtn').on('click', function(){
    $('#upload-table tbody').append(
      '<tr id="m'+fileIndex+'"><td>Files:'+(fileIndex+1)+'</td>'
     +'<td><input type=file name="files"></td></tr>'
    )
    fileIndex++
})
$('#removeBtn').on('click', function(){
    if (fileIndex > -1) { $('#m'+(fileIndex-1)).remove(); fileIndex-- }
})
```

- Vue 방식은 폼(`<form>`) 자체를 안 쓰고 **JS가 직접 `FormData`를 만들어 axios로 전송** — SPA/REST 구조에 어울림
- jQuery 방식은 여전히 `<form enctype="multipart/form-data">`로 **전통적인 폼 제출** 방식을 쓰되, 파일 입력칸 개수만 JS로 동적으로 늘리고 줄임 (같은 `name="files"`를 여러 개 두면 서버에서 `List<MultipartFile>`로 한 번에 받음)

