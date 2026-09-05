# 로컬 실행 & 테스트 가이드

아무것도 안 떠 있는 상태에서 시작해 브라우저로 기능을 확인하기까지의 전체 절차.

전제: Docker Desktop 실행 중, JDK 21, Node.js.

---

## 1. 사전에 입력해야 할 값

**일반 CRUD 테스트에는 없습니다.** 공공 식품 검색까지 테스트할 때만 `.env` 파일을 만듭니다.

`src/main/resources/application.yml`이 모든 값에 개발용 기본값을 갖고 있습니다.

| 값 | 기본값 | 조치 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/mealtalk` | 그대로 |
| `DB_USERNAME` | `mealtalk` | 그대로 |
| `DB_PASSWORD` | `mealtalk` | 그대로 |
| `JWT_SECRET` | 개발용 기본값 | 그대로 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8081` | 그대로 |
| `GOOGLE_CLIENT_ID` | (비어 있음) | **e2e 프로파일에서는 불필요** |
| `FOOD_API_KEY` | — | 공공 식품 검색을 사용할 때만 공공데이터포털 인증키 입력 |

앱 쪽 `mealtalk-app/.env`도 `.env.example`을 그대로 복사하면 됩니다. 기본값이 `http://localhost:8080`이라 수정할 게 없습니다.

### 공공 식품 검색용 키

`mealtalk-back/.env.example`을 `mealtalk-back/.env`로 복사하고 아래처럼 입력합니다.
`.env`는 Git에서 제외되며 Spring Boot가 직접 읽습니다.

```env
spring.profiles.active=e2e
FOOD_API_KEY=공공데이터포털_일반_인증키
```

키는 공공데이터포털의 **식품의약품안전처_식품영양성분DB정보**에서 발급받은 일반 인증키를
그대로 붙여넣으면 됩니다. 포털이 주는 키는 이미 percent-encoding 되어 있으며, 백엔드가
전송 전에 한 번 디코딩하므로 따로 변환할 필요가 없습니다.

실제 호출 대상은 아래 오퍼레이션입니다.

```text
https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02
```

`FOOD_API_KEY`는 Expo 앱 `.env`나 `EXPO_PUBLIC_*`에 넣으면 안 됩니다. 백엔드만
이 API를 호출합니다. 키가 비어 있으면 공공 검색만 오류가 나며, 기존 내 식품 검색과
식사 기록은 계속 사용할 수 있습니다.

### 구글 로그인 키는 왜 필요 없나

백엔드를 `e2e` 프로파일로 띄우면 구글 토큰 검증이 **고정 픽스처로 대체**됩니다.
`POST /api/v1/auth/google`에 `{"idToken":"mealtalk-e2e-id-token"}`을 보내면 실제 JWT가 발급됩니다.

화면의 "Google로 계속하기" 버튼 자체를 테스트하려면 그때만 Google Cloud 콘솔에서 OAuth 클라이언트 ID를 발급받아
`mealtalk-app/.env`의 `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`에 넣으세요. 아래 절차에는 필요 없습니다.

> `mealtalk-app/.env`는 `.gitignore`에 등록되어 있으니 키를 넣어도 커밋되지 않습니다.

---

## 2. 실행 (터미널 3개)

### 2-1. Postgres

```bash
docker start mealtalk-postgres
```

컨테이너가 없다면 새로 만듭니다.

```bash
docker run -d --name mealtalk-postgres \
  -e POSTGRES_DB=mealtalk -e POSTGRES_USER=mealtalk -e POSTGRES_PASSWORD=mealtalk \
  -p 5433:5432 postgres:17
```

확인:

```bash
docker exec mealtalk-postgres psql -U mealtalk -d mealtalk -c "select 1;"
```

### 2-2. 백엔드

PowerShell (`mealtalk-back` 디렉터리에서):

```powershell
cd mealtalk-back
.\gradlew.bat bootRun
```

Git Bash나 macOS/Linux라면:

```bash
cd mealtalk-back && ./gradlew bootRun
```

성공 신호 — 최초 실행이면 Flyway가 마이그레이션 3개를 적용합니다.

```
Successfully applied 3 migrations to schema "public", now at version v3
Tomcat started on port 8080 (http)
Started ApiApplication
```

### 2-3. 앱

새 터미널에서:

```bash
cd mealtalk-app
cp .env.example .env    # 최초 1회
npx expo start --web --port 8081
```

`Waiting on http://localhost:8081`이 뜨면 준비 완료입니다.

> 포트 8081은 백엔드 CORS 허용 목록과 **반드시 일치**해야 합니다. Expo가 다른 포트로 뜨면
> 백엔드를 `CORS_ALLOWED_ORIGINS=http://localhost:<그 포트>`로 다시 띄우세요.

---

## 3. 로그인

구글 클라이언트 ID가 없으므로 **로그인 버튼으로는 진입할 수 없습니다.** 토큰을 직접 넣습니다.

### 3-1. 토큰 발급

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/google \
  -H "Content-Type: application/json" \
  -d "{\"idToken\":\"mealtalk-e2e-id-token\"}"
```

응답의 `accessToken` 값을 복사합니다. 유효기간은 2시간입니다.

### 3-2. 브라우저에 주입

http://localhost:8081 을 연 뒤 devtools 콘솔(F12)에서:

```js
localStorage.setItem('mealtalk.access-token', '복사한_토큰');
location.reload();
```

앱이 저장된 토큰을 먼저 읽으므로 바로 로그인 상태로 들어갑니다.

---

## 4. 테스트 데이터 준비

빈 DB로 시작하면 식품이 없어 식사 기록을 만들 수 없습니다. 화면에서 직접 만들어도 되고, 아래로 한 번에 넣어도 됩니다.

```bash
TOKEN=붙여넣기
for f in \
  '{"name":"brown rice","servingAmount":100,"servingUnit":"g","caloriesKcal":150,"carbohydratesG":33,"proteinG":3,"fatG":1}' \
  '{"name":"chicken breast","servingAmount":100,"servingUnit":"g","caloriesKcal":165.2,"carbohydratesG":0,"proteinG":31.2,"fatG":3.6}' \
  '{"name":"archive target","servingAmount":100,"servingUnit":"g","caloriesKcal":100,"carbohydratesG":10,"proteinG":10,"fatG":1}'; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/foods \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "$f"
done
```

각 줄이 `201`이면 성공입니다.

> Windows 터미널에서 한글 이름이 `400`으로 튕기면 셸 인코딩 문제입니다. 서버 문제가 아니며,
> 화면에서 한글로 입력하면 정상 동작합니다.

---

## 5. 테스트 시나리오

프로필 → 식품 → 식사 순서로 진행합니다. 아래 세 개가 가장 중요합니다.

### A. 프로필

1. 프로필 탭에서 키·몸무게·활동량·목표 입력 후 저장. 새로고침해도 유지되는지.
2. **타깃 2개를 넣고 저장 → 1개로 줄여 다시 저장 → 새로고침.** 지운 타깃이 되살아나면 실패.
3. 타깃을 모두 지우고 저장. 0개로 저장되어야 합니다.
4. 목표일에 `2026-02-31` 같은 없는 날짜 입력. 필드 오류가 뜨고 입력값이 유지되어야 합니다.

> 활동량 값은 `LOW / MEDIUM / HIGH` 세 가지입니다.

### B. 식품

1. `FOOD_API_KEY`를 설정했다면 식단 추가 화면에서 `닭가슴살`을 검색 → **공공 식품** 결과 선택 →
   내 식품으로 저장되고 선택한 식품 목록에 추가되는지.
2. 새 식품 추가 → 목록 반영 → 새로고침 유지.
3. 검색어로 일부만 필터링되는지.
4. 수정 후 목록에 반영되는지.
5. **`archive target`을 보관 → 목록에서 사라짐 → 새로고침해도 안 보임.**
   보관은 삭제가 아니므로 과거 식사 기록에는 그대로 남아 있어야 합니다.
6. 제공량에 `0` 또는 소수점 넷째 자리(`1.2345`) 입력 시 저장이 막히는지.

### C. 식사

1. **식사 추가 → 식품 2개 담고 수량 입력 → 저장.**
   저장 후 하루 합계가 서버 확정값으로 표시되는지. 편집 중 "예상 영양"과 크게 어긋나면 기록해 주세요.
2. 수량을 2배로 수정 → 합계도 약 2배가 되는지.
3. **식사에 담은 식품을 "내 식품"에서 수정한 뒤, 그 식사를 다시 열기.**
   "식품 정보가 변경되었습니다" 안내가 뜨고, 예상 영양이 **최신 기준**으로 다시 계산되어야 합니다.
   저장 후 합계가 예상값과 일치해야 합니다.
4. **보관한 식품이 식사 추가 검색에 뜨지 않는지.**
   이미 담긴 식사를 열면 해당 항목이 "보관되었거나 삭제된 식품입니다"로 표시되고 저장이 막혀야 합니다.
5. 식품 없이 저장 시도 → "최소 한 가지 식품을 추가해주세요".
6. 이전/다음/오늘 날짜 이동 시 날짜별로 따로 조회되는지.
7. 삭제 → 확인 다이얼로그 → 목록에서 제거되고 합계가 0으로 복귀.

### D. 실패 경로

1. 콘솔에서 토큰을 망가뜨리고 새로고침 — 로그인 화면으로 복귀해야 하며 흰 화면이 되면 안 됩니다.

```js
localStorage.setItem('mealtalk.access-token', 'broken');
location.reload();
```

2. 백엔드를 끄고 날짜 이동 — 오류 안내와 재시도 버튼이 보이고, 보던 내용은 유지되어야 합니다.

---

## 6. 중점 확인 항목

앱 자동화 테스트가 없어 아래는 눈으로 봐야 합니다.

- **소수점 표기** — 서버는 `150.000` 형태로 응답합니다. 화면에 `150.000 g`으로 그대로 노출되면 표기 처리 문제입니다.
- **시간** — 입력한 시간과 카드에 표시되는 시간이 다르면 타임존 문제입니다.
- **한글** — 식품 이름이 깨지지 않는지.
- **콘솔** — 전 과정에서 uncaught 에러가 없어야 합니다.

---

## 7. 종료와 초기화

```bash
# 백엔드 / Expo: 각 터미널에서 Ctrl+C
# Postgres 정지 (데이터는 보존)
docker stop mealtalk-postgres
```

데이터를 완전히 비우고 다시 시작하려면:

```bash
docker exec mealtalk-postgres psql -U mealtalk -d mealtalk \
  -c "drop schema public cascade; create schema public; grant all on schema public to mealtalk;"
```

다음 백엔드 실행 때 Flyway가 마이그레이션을 처음부터 다시 적용합니다.

---

## 8. 문제 해결

### `Found non-empty schema(s) "public" but no schema history table`

Flyway 이력 없이 테이블만 있는 상태입니다. 과거에 `ddl-auto`로 만들어진 스키마가 남아 있을 때 발생합니다.
개발 DB라면 7절의 초기화 명령으로 해결됩니다.

### `Port 8080 was already in use`

이전 백엔드가 살아 있습니다. **이 경우 API가 응답하더라도 그건 낡은 서버입니다.**
포트를 점유한 프로세스를 종료하세요.

```bash
netstat -ano | grep ':8080 .*LISTENING'
taskkill //PID <PID> //F
```

기동 성공 여부는 포트 응답이 아니라 로그의 `Started ApiApplication`으로 판단하세요.

### 화면의 모든 API 호출이 실패

CORS 불일치입니다. Expo가 뜬 포트와 백엔드 `CORS_ALLOWED_ORIGINS`가 같은지 확인하세요.

```bash
curl -s -D - -o /dev/null -X OPTIONS http://localhost:8080/api/v1/me \
  -H "Origin: http://localhost:8081" -H "Access-Control-Request-Method: GET"
```

`Access-Control-Allow-Origin` 헤더가 응답에 있어야 합니다.

### 401이 계속 발생

토큰이 만료(2시간)됐습니다. 3절로 재발급하세요.
