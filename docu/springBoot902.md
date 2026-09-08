# AWS 배포 - Docker Compose 방식

**▶ 워크플로우 전체 (Repository Secrets 사용)**

```yaml
name: Deploy With Docker to AWS Server
on:
  push:
    branches:
      - main            # main에 commit+push 될 때만 실행

jobs:
  deploy:
    runs-on: ubuntu-latest   # AWS EC2가 아니라 GitHub Actions가 임시로 제공하는 우분투

    steps:
      # 1) 저장소 코드(src, build.gradle, gradlew, Dockerfile 등)를 가져옴
      - name: Checkout:repository
        uses: actions/checkout@v4

      # 2) Spring Boot 프로젝트 빌드를 위한 JDK 설정
      - name: SetUp JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'   # 이클립스 재단이 배포하는 오픈소스 JDK
          java-version: '21'

      # 3) gradlew 실행 권한 부여
      - name: Set gradlew permission
        run: |
          chmod +x ./gradlew

      # 4) 테스트 제외하고 빌드
      - name: Build with Gradlew
        run: ./gradlew clean build -x test

      # 5) DockerHub 로그인 - 비밀번호를 표준입력(stdin)으로 전달해서 노출 방지
      - name: Login DockerHub
        run: |
          echo "${{secrets.DOCKER_PASSWORD}}" |\
          docker login \
          -u "${{secrets.DOCKER_USERNAME}}" \
          --password-stdin

      # 6) 현재 디렉토리의 Dockerfile로 이미지 빌드, 이름:태그를 지정
      - name: Build Docker Image
        run: |
          docker build \
           -t ${{secrets.DOCKER_USERNAME}}/last-app:latest .

      # 7) 빌드한 이미지를 DockerHub 저장소로 업로드
      - name: Push Docker Image
        run: |
          docker push \
           ${{secrets.DOCKER_USERNAME}}/last-app:latest

      # 8) AWS 서버에 SSH로 접속하기 위한 개인키를 이 임시 환경에 준비
      - name: Set SSH key Permission
        run: |
          mkdir -p ~/.ssh
          echo "${{secrets.SERVER_SSH_KEY}}" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519   # 권한이 넓으면 SSH가 키 사용을 거부

      # 9) 만든 키를 ssh-agent에 등록해서 이후 ssh/scp가 자동으로 사용하게 함
      - name: Add SSH Key
        uses: webfactory/ssh-agent@v0.5.3
        with:
          ssh-private-key: ${{secrets.SERVER_SSH_KEY}}

      # 10) 처음 접속하는 서버 지문을 미리 신뢰 목록에 등록 (대화형 확인 생략)
      - name: Add Known_hosts
        run: |
          ssh-keyscan -H 54.180.150.51 >> ~/.ssh/known_hosts

      # 11) 서버에 접속해서 DB 접속 정보가 담긴 .env 파일을 새로 생성
      - name: 환경설정
        run: |
           ssh ubuntu@54.180.150.51 << EOF
            # 기존 파일 삭제 후 새로 작성 (매 배포마다 최신 Secrets 값으로 갱신)
            mkdir -p ~/app
            cd ~/app
            rm -f .env

            echo "DB_URL=${{secrets.DB_URL}}" >> ~/app/.env
            echo "DB_USERNAME=${{secrets.DB_USERNAME}}" >> ~/app/.env
            echo "DB_PASSWORD=${{secrets.DB_PASSWORD}}" >> ~/app/.env
            chmod 600 ~/app/.env
           EOF

      # 12) docker-compose.yml 파일 자체를 서버로 복사 (compose 설정도 최신 상태로 유지)
      - name: SCP docker-compose
        run: |
          ssh ubuntu@54.180.150.51 "mkdir -p /home/ubuntu/app"
          scp docker-compose.yml ubuntu@xx.xxx.xxx.xxx:/home/ubuntu/app/docker-compose.yml

      # 13) 서버에서 최신 이미지를 받아 컨테이너를 내리고 다시 올림
      - name: DockerHub Image using
        run: |
          ssh ubuntu@xx.xxx.xxx.xxx << 'EOF'
           cd /home/ubuntu/app
           docker-compose pull    # DockerHub에서 최신 이미지 다시 받기
           docker-compose down    # 기존 컨테이너 정지 + 제거
           docker-compose up -d   # 새 이미지로 컨테이너 재생성, 백그라운드 실행
          EOF
```

---

**▶ jar+rsync 방식과 무엇이 다른가**

| 구분 | jar + rsync | Docker Compose |
|---|---|---|
| 서버로 옮기는 것 | 빌드된 jar 파일 자체 | DockerHub에 올린 이미지 (서버는 `pull`만 함) |
| 서버에서 실행하는 방법 | `nohup java -jar ...` 직접 실행 | `docker-compose up -d` |
| 필요한 추가 인프라 | 없음 (자바만 있으면 됨) | Docker, DockerHub 계정, `docker-compose.yml` |
| 여러 컨테이너(앱+DB 등) 관리 | 어려움(직접 스크립트로 관리해야 함) | `docker-compose.yml` 한 파일로 여러 서비스 한 번에 관리 가능 |
| 실행 환경 일관성 | 서버에 설치된 JDK 버전에 의존 | 이미지 안에 실행 환경이 고정되어 있어 서버 환경 차이 영향이 적음 |

- 두 방식 모두 **SSH 키 준비 → known_hosts 등록** 과정은 동일 — 배포 "이후 단계"(무엇을 어떻게 실행할지)만 jar 직접 실행이냐 컨테이너 재기동이냐로 갈림
- `.env` 파일에 DB 접속정보를 담아두는 것도 공통 — Docker Compose 방식에서는 이 `.env`를 `docker-compose.yml`이 컨테이너 실행 시 환경변수로 읽어들이는 용도로 사용
