# Approval System

Spring Boot 3.5.10 + Kotlin 2.1.0 기반 최소 결재 시스템 구현

## 핵심 구현 증명

1. **상태머신 (Server-driven State Machine)**
   - `Approval.kt:49-70` - 서버가 상태 전이를 제어
   - DRAFT → IN_PROGRESS → APPROVED 자동 전환
   - ACTIVE step만 승인 가능

2. **트랜잭션 경계 (Transaction Boundary)**
   - `ApprovalService.kt:22` - `@Transactional` 단일 트랜잭션
   - 검증 → 상태 전이 → 액션 로그 → Outbox 이벤트 저장이 하나의 원자적 작업

3. **멱등성 (Idempotency)**
   - `ApprovalActionLog.kt:10-15` - Unique constraint로 중복 방지
   - `ApprovalService.kt:25-35` - 같은 idempotencyKey 요청 시 동일 결과 반환

4. **Outbox Pattern**
   - `OutboxEvent.kt` - 이벤트를 DB에 먼저 저장
   - `ApprovalService.kt:75-102` - 트랜잭션 내에서 이벤트 저장
   - 향후 별도 프로세스가 PENDING 이벤트를 폴링하여 메시지 브로커로 발행 (현재는 저장만)

## 시작하기

### 사전 요구사항

- **JDK 21 (LTS)** 이상 설치 필요
- Docker (PostgreSQL 실행용)

JDK 설치 확인:
```bash
java -version
# java version "21.x.x" 출력되어야 함
```

JDK 21이 없다면 [Adoptium](https://adoptium.net/) 또는 [Oracle JDK](https://www.oracle.com/java/technologies/downloads/#java21)에서 설치하세요.

### 1. PostgreSQL 실행

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
# Windows
.\gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

### 3. 테스트 실행

```bash
# Windows
.\gradlew.bat test

# Linux/Mac
./gradlew test
```

## API 사용 예시

### 준비: 데이터베이스에 Approval 생성

먼저 테스트용 데이터를 수동으로 생성해야 합니다. PostgreSQL에 접속하여:

```sql
-- Approval 생성 (DRAFT 상태)
INSERT INTO approvals (id, status, version, created_at, updated_at)
VALUES ('123e4567-e89b-12d3-a456-426614174000', 'IN_PROGRESS', 0, NOW(), NOW());

-- Step 1 (ACTIVE)
INSERT INTO approval_steps (id, approval_id, step_order, assignee_id, status, created_at, updated_at)
VALUES ('223e4567-e89b-12d3-a456-426614174000', '123e4567-e89b-12d3-a456-426614174000', 1, '323e4567-e89b-12d3-a456-426614174000', 'ACTIVE', NOW(), NOW());

-- Step 2 (PENDING)
INSERT INTO approval_steps (id, approval_id, step_order, assignee_id, status, created_at, updated_at)
VALUES ('333e4567-e89b-12d3-a456-426614174000', '123e4567-e89b-12d3-a456-426614174000', 2, '423e4567-e89b-12d3-a456-426614174000', 'PENDING', NOW(), NOW());
```

### 승인 API 호출

**Step 1 승인:**

```bash
curl -X POST http://localhost:8080/api/approvals/123e4567-e89b-12d3-a456-426614174000/steps/223e4567-e89b-12d3-a456-426614174000/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approverId": "323e4567-e89b-12d3-a456-426614174000",
    "idempotencyKey": "my-unique-key-001"
  }'
```

**응답:**

```json
{
  "approvalId": "123e4567-e89b-12d3-a456-426614174000",
  "approvalStatus": "IN_PROGRESS",
  "version": 1,
  "activeStepId": "333e4567-e89b-12d3-a456-426614174000",
  "activeStepStatus": "ACTIVE",
  "activeStepOrder": 2
}
```

**Step 2 승인 (최종):**

```bash
curl -X POST http://localhost:8080/api/approvals/123e4567-e89b-12d3-a456-426614174000/steps/333e4567-e89b-12d3-a456-426614174000/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approverId": "423e4567-e89b-12d3-a456-426614174000",
    "idempotencyKey": "my-unique-key-002"
  }'
```

**응답:**

```json
{
  "approvalId": "123e4567-e89b-12d3-a456-426614174000",
  "approvalStatus": "APPROVED",
  "version": 2,
  "activeStepId": null,
  "activeStepStatus": null,
  "activeStepOrder": null
}
```

### 멱등성 검증

같은 idempotencyKey로 다시 호출:

```bash
curl -X POST http://localhost:8080/api/approvals/123e4567-e89b-12d3-a456-426614174000/steps/223e4567-e89b-12d3-a456-426614174000/approve \
  -H "Content-Type: application/json" \
  -d '{
    "approverId": "323e4567-e89b-12d3-a456-426614174000",
    "idempotencyKey": "my-unique-key-001"
  }'
```

→ 동일한 결과 반환 (중복 처리 방지)

## 아키텍처

```
github.lms.approval
├── api/                    # Controller, Request/Response DTOs
├── application/            # Service, Command, Result
├── domain/                 # Entities, Enums, State Machine
└── infra/                  # Repositories, Outbox
```

## 기술 스택

- Kotlin 2.1.0
- JDK 21 (LTS)
- Spring Boot 3.5.10
- Spring Data JPA
- PostgreSQL 16
- Testcontainers (테스트용)

## 제약 사항

- **단일 API만 제공**: POST /api/approvals/{approvalId}/steps/{stepId}/approve
- **인증/인가 없음**: approverId는 요청 body로 전달
- **외부 알림 없음**: Outbox 이벤트는 DB에만 저장 (발행 로직은 구현 안 함)
- **Approval 생성 API 없음**: 테스트는 직접 엔티티를 저장하여 검증
