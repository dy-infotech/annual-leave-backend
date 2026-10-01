# IMPLEMENTATION

이 문서는 `annual-leave-backend`의 구현 불변조건과 예외 규칙을 정리합니다.
프로젝트 개요, 기술 스택, API 목록은 `README.md`를 기준으로 하고, 여기서는 코드를 수정할 때 유지해야 하는 동작을 중심으로 설명합니다.


## 1. 기본 전제

- 운영은 **단일 애플리케이션 인스턴스**를 전제로 합니다.
- Redis, Kafka 같은 외부 분산 인프라는 사용하지 않습니다.
- 애플리케이션 캐시는 Caffeine 기반 로컬 캐시입니다.
- Oracle 스키마 정본은 `sql/schema.sql`입니다.
- Hibernate는 `ddl-auto=validate`를 사용하므로 Entity와 DB 타입/컬럼 정의가 일치해야 합니다.
- p6spy 로그의 마스킹 처리는 의도된 정책입니다.


## 2. 인증 / SSO

### Access / Refresh 역할

- Access Token은 JWT이며 요청 인증에 사용합니다.
- Refresh Token은 HttpOnly Cookie로 전달하고 `AUTH_REFRESH_SESSION`에서 Rotation 상태를 관리합니다.
- Refresh Token 원문은 DB에 저장하지 않고 hash만 저장합니다.
- Refresh 세션은 idle 만료와 absolute 만료를 각각 가집니다. Rotation은 absolute 만료를 연장하지 않습니다.
- `/refresh`, `/session-marker`, `/logout`의 Refresh Cookie 동작은 `X-SSO-Refresh: 1` 헤더를 요구합니다.

### Refresh Token Rotation

`RefreshTokenService.rotate()`는 해당 session row를 write lock으로 읽습니다.

- 현재 generation/token이면 새 generation으로 rotation합니다.
- 직전 token이 replay grace 안에서 재전송된 경우에는 이미 발급된 현재 token을 재현할 수 있는 경우에만 정상 응답합니다.
- grace를 벗어난 이전 token 재사용은 `REUSE_DETECTED`로 session을 폐기합니다.
- 만료/폐기/사용자 상태 이상은 refresh 실패로 처리합니다.
- session marker가 전달된 요청은 현재 Access 세션과 Refresh 세션이 동일한지 함께 검증합니다.

로그아웃의 background 요청은 이전 세션의 늦은 응답이 새 로그인 세션의 cookie나 FCM binding을 지우지 않도록 session marker를 사용합니다.

### 비밀번호 변경 / 재설정

- 비밀번호 변경 또는 재설정 성공 시 해당 사용자의 활성 Refresh Session을 폐기합니다.
- 비밀번호 재설정 token은 15분 유효한 일회성 token이며 DB에는 hash만 저장합니다.
- 새 재설정 요청을 만들 때 기존 미사용 token을 정리합니다.
- token 검증 및 소비는 write lock 하에서 처리합니다.


## 3. 공개 인증 API Rate Limit

`AuthRateLimitService`는 Caffeine 기반의 **인스턴스 로컬** 제한입니다.

- 로그인: 사번 기준 10회/10분, IP 기준 30회/1분
- 계정 복구: 식별자 기준 3회/10분, IP 기준 10회/10분
- 기타 공개 인증 API: route+식별자 기준 10회/10분, route+IP 기준 30회/10분
- 내부 SYSTEM context는 제한하지 않습니다.

분산 rate limit으로 간주하면 안 됩니다. 단일 인스턴스 전제에서 짧은 구간 abuse 완화를 위한 방어입니다.


## 4. 조직 / 권한

### 조직 모델

- `DEPARTMENT`: 부서
- `TEAM`: 팀과 소속 부서
- `EMPLOYEE.department_id / team_id`: 직원의 현재 조직
- `TEAM_MANAGER`: 팀 관리자(PM)와 상위 팀 관계
- `EMPLOYEE.approver_id`: 조회/호환용 결재자 snapshot이며 **최종 권한 근거가 아닙니다.**

### 현재 권한이 정본

JWT의 role은 로그인 시점 snapshot입니다.

실제 관리자 권한은 항상 현재 DB/조직 상태를 다시 확인합니다.

- `/api/admin/**`: `AdminAuthorizationInterceptor`
- 일반 관리자: 현재 재직 + 현재 TeamManager 여부
- 부서/팀 관리: `@RequirePersonnelAuthority`
- 휴가 승인/반려: 현재 조직 구조에서 계산한 해당 신청의 실제 결재권

따라서 PM 승격/해제 후 기존 JWT를 다시 발급하지 않아도 서버 권한 판정은 현재 조직 상태를 따릅니다.

### 조직 변경 동시성

조직 parent-edge 또는 PM 변경은 대표/root 팀 row를 공통 mutex로 사용하는 `lockHierarchyForUpdate()`를 먼저 획득합니다.

그 뒤 필요한 TEAM/EMPLOYEE row를 정렬된 순서로 write lock하여 다음을 방지합니다.

- 같은 팀의 PM 추가/삭제/교체 경합
- parent team 변경 중 cycle 생성
- PM 해제로 인한 결재 공백
- 사원 조직 변경과 조직 관리 변경 사이의 stale snapshot 사용

TEAM_MANAGER 변경과 같은 transaction에서 approver를 다시 계산할 때는 committed cache만 그대로 사용하지 않고 **현재 요청의 add/remove delta를 합성**합니다.

### 결재 공백 방지

관리자 제거, 퇴사일 변경, 팀 관계 변경 후에는 향후 결재 가능한 관리자 coverage를 검증합니다.

- 하위 팀 또는 결재 대상 직원이 존재하는 팀은 유효한 관리자 없이 남을 수 없습니다.
- 상위 팀을 지정할 때 상위 팀의 활성 관리자 존재 여부를 확인합니다.
- 조직 cycle은 허용하지 않습니다.


## 5. 휴가 신청

### 생성

휴가 생성의 서버 정본은 `LeaveRequestService.createLeaveRequest()`입니다.

- 휴가 종류와 기간을 서버에서 검증합니다.
- 공휴일/주말을 반영해 사용 일수를 서버에서 계산합니다.
- PENDING/APPROVED 신청과 기간 중복 여부를 서버에서 검사합니다.
- 선택적 `Idempotency-Key`를 지원합니다.
- 동일 직원 + 동일 key + 동일 payload 재시도는 기존 결과를 재사용합니다.
- 동일 key를 다른 payload에 재사용하면 충돌로 거부합니다.
- DB의 조건부 unique index가 최종 중복 방어선입니다.

### 상태

주요 상태는 다음과 같습니다.

```text
PENDING -> APPROVED
PENDING -> REJECTED
PENDING -> CANCELLED
APPROVED -> CANCELLED  (시작일 전까지만)
```

승인/반려는 repository 조건부 UPDATE에서 기존 상태가 `PENDING`인지 함께 비교합니다.
동시 처리로 UPDATE 건수가 0이면 최신 상태를 다시 읽고:

- 같은 관리자가 동일 결과를 재전송한 경우 기존 성공 결과를 반환
- 다른 결과/관리자의 처리라면 conflict

로 처리합니다.

### 취소

- 본인의 신청만 취소할 수 있습니다.
- PENDING 또는 APPROVED만 취소할 수 있습니다.
- APPROVED 상태에서 휴가 시작일이 오늘이거나 이미 지난 경우 취소할 수 없습니다.
- 같은 취소 요청의 재전송은 최종 CANCELLED 상태를 안정적으로 처리합니다.


## 6. 승인 대상 범위

승인 관련 조회 기간은 **현재 연도 전체**가 의도된 정책입니다.

### PENDING

PENDING 조회는 관리자의:

- 직접 관리팀 직원 신청
- 직접 관리팀에 연결된 하위 팀장 신청

을 대상으로 합니다.

root 관리자는 별도의 자기 제외 조건을 적용하지 않습니다.

### APPROVED / REJECTED

처리 완료 목록은 현재 관리 가능한 팀과 그 하위 팀 전체를 `accessibleTeams`로 계산합니다.

요청한 team 필터가 현재 접근 범위 밖이면 빈 결과를 반환합니다.


## 7. 목록 / 페이지네이션

휴가 목록의 응답 정본은 다음 구조입니다.

```java
PageResponseDto<T>(
    List<T> items,
    long totalCount,
    boolean hasMore
)
```

### 원칙

- `totalCount`: **동일 검색조건으로 DB에서 COUNT한 전체 건수**
- `items`: 현재 요청에서 실제로 반환하는 row
- `hasMore`: backend가 결정
- frontend의 현재 로드 개수(`items.length`)를 전체 건수로 사용하면 안 됩니다.

### OFFSET 첫 조회

- 기본 `size=50`
- 허용 `size`: 1~100
- 과도한 OFFSET은 제한합니다.
- 첫 page 요청의 `hasMore`는 `totalCount`와 `page/size`로 판정합니다.

### Cursor 무한스크롤

이후 요청은 다음 복합 cursor를 사용합니다.

- 일반 신청 목록: `cursorRequestedAt + cursorRequestId`
- 승인 목록: `cursorCreatedAt + cursorRequestId`

둘 중 하나만 전달하는 것은 허용하지 않습니다.

cursor 조회는 `size + 1`개를 읽어 실제 다음 row 존재 여부를 판정하고, 사용자에게는 최대 `size`개만 반환합니다.

**200건 hard cap은 없습니다.** Cursor가 계속 존재하면 200건을 넘어 계속 조회되어야 합니다.


## 8. 캐시

캐시는 DB의 대체 저장소가 아니라 read 최적화 계층입니다.

주요 캐시:

- 직원 조회: 30분
- 조직/팀 관리 데이터: 24시간
- 공휴일: 24시간
- 이메일 검색: 1시간

write path는 관련 DB 변경이 commit된 이후 cache invalidation이 적용되도록 구성합니다.

같은 transaction 내부에서 아직 commit되지 않은 조직 변경을 읽어야 하는 로직은 cache snapshot만 믿지 않고 요청 delta 또는 DB lock 기반 조회를 사용합니다.


## 9. FCM

FCM token은 한 token의 현재 owner를 DB 기준으로 관리합니다.

- token 동기화/로그아웃 작업은 같은 token 단위로 직렬화합니다.
- owner 변경 중 Firebase topic 작업과 DB binding이 엇갈리면 현재 DB owner를 다시 읽어 보상합니다.
- Web SSO client는 `auth_session_marker`를 함께 저장해 이전 세션의 stale logout이 새 세션 token을 제거하지 못하게 합니다.
- 로그아웃 후 FCM 정리 실패는 인증 세션 폐기 자체를 실패시키지 않습니다.
- 알림 전송은 transaction commit 이후 실행합니다.


## 10. 공휴일

휴가 일수 계산과 캘린더에 사용하는 공휴일은 `HolidaySyncService`를 통해 조회합니다.

- 휴가 기간 계산 시 필요한 연도 범위를 서버가 조회합니다.
- 외부 공휴일 API 결과는 Oracle `HOLIDAY`와 로컬 캐시를 통해 재사용합니다.
- 클라이언트가 계산한 휴가 일수보다 서버 계산 결과가 정본입니다.


## 11. DB / SQL

현재 유지하는 SQL은 세 개입니다.

- `sql/schema.sql`: 현재 Oracle 스키마 정본
- `sql/data.sql`: 개발/CI seed
- `sql/dba_prepare_common_auth_tablespace.sql`: `COMMON_DATA / COMMON_INDEX` DBA 준비

완료된 v1→v2 migration/repair SQL은 정본이 아니므로 저장소에서 제거했습니다.

스키마 변경 시 최소한 다음을 함께 맞춰야 합니다.

1. Entity/JPA mapping
2. `sql/schema.sql`
3. 관련 QueryDSL/Repository
4. Oracle integration/regression test
5. 공유 스키마를 읽는 다른 시스템 영향


## 12. 변경 시 지켜야 할 기준

- 서버가 날짜, 권한, 조직 범위, 전체 건수의 신뢰의 근원입니다.
- JWT role snapshot만으로 현재 관리자 권한을 확정하지 않습니다.
- 조직 write에서 hierarchy/team/employee lock 순서를 임의로 바꾸지 않습니다.
- PENDING 상태 전이는 read-check-write만으로 바꾸지 말고 조건부 UPDATE를 유지합니다.
- 페이지 전체 건수는 로드된 item 개수가 아니라 동일 조건의 DB count를 사용합니다.
- cursor 정렬 컬럼과 cursor 조건을 따로 변경하지 않습니다.
- cache invalidation을 transaction commit보다 먼저 수행하지 않습니다.
- 비밀번호/Refresh Token 원문을 DB나 로그에 남기지 않습니다.
- p6spy 마스킹을 제거하거나 우회하지 않습니다.
- 단일 인스턴스 전제를 바꾸지 않는 한 Redis/Kafka 같은 외부 인프라를 추가하지 않습니다.
