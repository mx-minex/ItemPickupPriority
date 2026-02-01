# ItemPickupPriority (발자)

아이템 줍기 우선순위를 관리하는 마인크래프트 플러그인입니다.

## 개요

ItemPickupPriority는 여러 플레이어가 드롭된 아이템 근처에 있을 때, 우선순위에 따라 아이템을 줍을 수 있는 플레이어를 결정합니다. 낮은 순위 번호를 가진 플레이어가 높은 우선순위를 갖습니다.

### 주요 기능

- 우선순위 기반 아이템 줍기 제어
- 플레이어 랭킹 시스템 (접속 순서 기반)
- 자동 순위 만료 시스템
- MySQL 지원으로 프록시 서버 환경 호환
- PlaceholderAPI 연동 지원

## 요구사항

- **Minecraft 버전:** 1.20 이상
- **서버:** Spigot / Paper
- **Java:** 17 이상

## 설치 방법

1. [Releases](https://github.com/your-repo/ItemPickupPriority/releases)에서 최신 버전의 JAR 파일을 다운로드합니다.
2. 서버의 `plugins` 폴더에 JAR 파일을 넣습니다.
3. 서버를 재시작합니다.
4. `plugins/ItemPickupPriority/config.yml`에서 설정을 커스터마이즈합니다.

## 명령어

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/발자` | 본인의 현재 순위 및 상위 10명 확인 | `itempickuppriority.command` |
| `/발자 순위 [페이지]` | 전체 순위 목록 조회 | `itempickuppriority.command` |
| `/발자 초기화` | 모든 순위 초기화 | `itempickuppriority.admin` |
| `/발자 순위변경 <플레이어> <순위>` | 플레이어 순위 변경 | `itempickuppriority.admin` |
| `/발자 리로드` | 설정 파일 리로드 | `itempickuppriority.admin` |

**명령어 별칭:** `/balja`, `/pickup`, `/itempickup`

## 권한

| 권한 | 설명 | 기본값 |
|------|------|--------|
| `itempickuppriority.*` | 모든 권한 | OP |
| `itempickuppriority.command` | 기본 명령어 사용 | 모든 플레이어 |
| `itempickuppriority.admin` | 관리자 명령어 | OP |

## 설정

### config.yml

```yaml
# 작동 모드 설정
operation-mode: STANDALONE  # STANDALONE (SQLite) 또는 PROXY_MYSQL (MySQL)

# SQLite 설정 (STANDALONE 모드)
sqlite:
  file: "data.db"

# MySQL 설정 (PROXY_MYSQL 모드)
mysql:
  host: "localhost"
  port: 3306
  database: "itempickuppriority"
  username: "root"
  password: ""
  pool-size: 10

# 순위 만료 설정
expiration:
  time-mode: REAL_TIME      # REAL_TIME 또는 SERVER_TIME
  duration-minutes: 10      # 오프라인 시 순위 만료 시간 (분)

# 캐시 설정
cache:
  offline-ttl-seconds: 300  # 오프라인 플레이어 캐시 TTL
  max-size: 10000           # 최대 캐시 항목 수
  ranking-ttl-ms: 5000      # 순위 목록 캐시 TTL

# 자동 저장 설정
auto-save:
  interval-seconds: 300     # 저장 간격
  dirty-only: true          # 변경된 데이터만 저장

# 디버그 모드
debug: false
```

### 시간 모드 설명

- **REAL_TIME:** 실제 시간 기준 (서버 꺼져도 시간 경과)
- **SERVER_TIME:** 서버 가동 시간 기준 (서버 꺼지면 시간 정지)

## 작동 방식

1. 플레이어가 서버에 접속하면 접속 순서에 따라 순위가 부여됩니다.
2. 아이템이 드롭되면, 근처에 있는 플레이어들의 순위를 비교합니다.
3. 가장 높은 우선순위(낮은 순위 번호)를 가진 플레이어만 아이템을 주울 수 있습니다.
4. 플레이어가 오프라인 상태가 되면 설정된 시간 후 순위가 만료됩니다.

## 프록시 서버 지원

BungeeCord 또는 Velocity 프록시 환경에서 사용할 경우:

1. `operation-mode`를 `PROXY_MYSQL`로 설정합니다.
2. MySQL 연결 정보를 입력합니다.
3. 모든 서버에서 동일한 MySQL 데이터베이스를 사용하도록 설정합니다.

## PlaceholderAPI 연동

PlaceholderAPI가 설치되어 있으면 자동으로 플레이스홀더가 등록됩니다:

- `%itempickuppriority_rank%` - 플레이어의 현재 순위

## 빌드 방법

```bash
./gradlew build
```

빌드된 JAR 파일은 `build/libs/` 폴더에 생성됩니다.

## 라이선스

이 프로젝트는 [GNU General Public License v3.0](LICENSE) 하에 배포됩니다.

## 개발자

- **Minex**
