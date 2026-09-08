# AWS 배포 - jar + rsync 방식

**▶ 워크플로우 전체 (Repository Secrets 사용)**

Settings → **Secrets and variables → Actions → Repository secrets**에 등록해둔 값을 `${{ secrets.이름 }}`으로 참조

```yaml
name: Deploy to AWS Server
on:
  push:
    branches:
      - main            # main에 commit+push 될 때만 실행 (master로 운영하는 경우 여기에 추가)

jobs:
  build:
    runs-on: ubuntu-latest   # GitHub이 제공하는 임시 우분투 환경에서 빌드 (AWS 서버 아님)

    steps:
      # 1) 저장소 코드를 이 임시 환경으로 내려받음
      - name: Checkout:repository
        uses: actions/checkout@v2

      # 2) 프로젝트가 21버전 JDK로 빌드되므로 동일 버전 설치
      - name: SetUp JDK 21
        uses: actions/setup-java@v1
        with:
          java-version: '21'

      # 3) gradlew 스크립트에 실행 권한 부여 (권한 없으면 다음 단계에서 Permission denied)
      - name: Set gradlew permission
        run: |
          chmod +x ./gradlew

      # 4) 실제 빌드 - 테스트는 시간 절약을 위해 건너뜀(-x test)
      - name: Build with Gradlew
        run: ./gradlew clean build -x test

      # 5) AWS 서버 접속용 SSH 개인키를 이 환경에 임시 생성
      - name: Set SSH key Permission
        run: |
          mkdir -p ~/.ssh
          echo "${{secrets.SERVER_SSH_KEY}}" > ~/.ssh/id_ed25519   # Secrets에 저장해둔 개인키 내용을 파일로 저장
          chmod 600 ~/.ssh/id_ed25519                               # 소유자만 읽기/쓰기 권한 (안 하면 SSH가 키 사용을 거부함)

      # 6) 만든 키를 ssh-agent에 등록 - 이후 ssh/rsync 명령이 자동으로 이 키를 사용
      - name: Add SSH Key
        uses: webfactory/ssh-agent@v0.5.3
        with:
          ssh-private-key: ${{secrets.SERVER_SSH_KEY}}

      # 7) 처음 접속하는 서버를 미리 신뢰 목록에 등록 (대화형 확인 절차를 CI에서는 건너뛰어야 함)
      - name: Add Known_hosts
        run: |
          ssh-keyscan -H ed25519 xx.xxx.xxx.xxx >> ~/.ssh/known_hosts

      # 8) 빌드된 jar 파일을 rsync로 AWS 서버의 ~/app 폴더에 전송
      - name: Deploy to Server with rsync
        run: |
          rsync -avz -e "ssh -o StrictHostKeyChecking=no" build/libs/*.jar ubuntu@xx.xxx.xxx.xxx:~/app

      # 9) 서버에 접속해서 .env를 읽어들인 뒤, 새 jar를 백그라운드로 재실행
      - name: Run command
        run: |
          ssh -i ~/.ssh/id_ed25519 ubuntu@xx.xxx.xxx.xxx << 'EOF'
            cd ~/app &&
            set -a &&
            source .env &&
            set +a &&
            nohup java -jar ~/app/SpringBootLastProject-0.0.1-SNAPSHOT.jar > log.txt 2>&1 &
          EOF
```

---

**▶ 단계별로 조금 더 풀어서**

| 단계 | 하는 일 |
|---|---|
| Checkout | 워크플로우가 실행 중인 임시 우분투에 내 저장소 코드를 내려받음 |
| SetUp JDK 21 | 프로젝트 빌드에 필요한 자바 버전 설치 |
| gradlew 권한 부여 | 리눅스에서 스크립트를 실행하려면 실행 권한(`x`)이 있어야 함 |
| Gradle Build | 실제 jar 파일 생성 (`build/libs/`에 결과물이 생김) |
| SSH 키 준비 + known_hosts | AWS 서버에 사람 개입 없이 SSH로 접속하기 위한 사전 준비 |
| rsync로 전송 | 빌드된 jar 파일만 서버로 복사 (Docker 이미지가 아니라 jar 파일 자체를 그대로 옮기는 방식) |
| 원격 실행 | 서버에 SSH로 들어가서, 환경변수(`.env`)를 로드하고 jar를 백그라운드 프로세스로 재실행 |

**▶ 각 명령의 의미**

- `rsync -avz -e "ssh -o StrictHostKeyChecking=no" 원본 대상`
  - `-a`: 권한/시간 등 속성을 유지하며 복사(archive 모드)
  - `-v`: 진행 상황을 출력(verbose)
  - `-z`: 전송 중 압축해서 속도 향상
  - `-e "ssh -o StrictHostKeyChecking=no"`: 전송 방식으로 SSH를 쓰되, 호스트 확인 절차를 건너뜀(known_hosts 등록과 비슷한 목적)
- `set -a` / `source .env` / `set +a`
  - `set -a`: 이후 선언되는 변수를 전부 자동으로 **환경변수로 내보내기(export)** 상태로 전환
  - `source .env`: `.env` 파일 안의 `KEY=VALUE` 줄들을 현재 셸에 변수로 불러옴 — `set -a`가 켜져 있어서 이 변수들이 자동으로 환경변수가 됨
  - `set +a`: 자동 export 모드를 다시 끔 (이후 변수는 평범한 셸 변수로 취급)
- `nohup java -jar 파일 > log.txt 2>&1 &`
  - `nohup`: SSH 세션이 끊겨도(즉 이 배포 스크립트가 끝나도) 자바 프로세스가 계속 살아있게 함
  - `> log.txt 2>&1`: 표준출력과 표준에러를 모두 `log.txt`로 저장
  - `&`: 백그라운드로 실행해서, 이 명령 실행 후 스크립트가 다음 줄로(=워크플로우 종료로) 바로 넘어가게 함

> 이 방식은 이전에 실행 중이던 jar 프로세스를 명시적으로 종료하는 단계가 없음 — 포트가 이미 사용 중이면 새 프로세스가 뜨지 못하거나 충돌할 수 있어서, 실제로는 배포 전에 기존 프로세스를 `kill`하는 단계가 보통 추가로 필요함(주석에 남겨둔 "9090 포트 Kill" 메모가 이 부분을 의미하는 것으로 보임)
