package github.lms.approval

import com.fasterxml.jackson.databind.ObjectMapper
import github.lms.approval.api.ApproveRequest
import github.lms.approval.api.ApproveResponse
import github.lms.approval.domain.Approval
import github.lms.approval.domain.ApprovalStatus
import github.lms.approval.domain.ApprovalStep
import github.lms.approval.infra.ApprovalActionLogRepository
import github.lms.approval.infra.ApprovalRepository
import github.lms.approval.infra.OutboxEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var approvalRepository: ApprovalRepository

    @Autowired
    private lateinit var actionLogRepository: ApprovalActionLogRepository

    @Autowired
    private lateinit var outboxEventRepository: OutboxEventRepository

    @BeforeEach
    fun setUp() {
        actionLogRepository.deleteAll()
        outboxEventRepository.deleteAll()
        approvalRepository.deleteAll()
    }

    @Test
    fun `approve success - two step approval`() {
        // Given: 2-step approval in DRAFT status
        val approvalId = UUID.randomUUID()
        val step1Id = UUID.randomUUID()
        val step2Id = UUID.randomUUID()
        val approver1Id = UUID.randomUUID()
        val approver2Id = UUID.randomUUID()

        val step1 = ApprovalStep(step1Id, 1, approver1Id)
        val step2 = ApprovalStep(step2Id, 2, approver2Id)
        val approval = Approval(approvalId, steps = mutableListOf(step1, step2))
        approval.startApprovalProcess()

        approvalRepository.save(approval)

        // When: Approve step 1
        val idempotencyKey1 = UUID.randomUUID().toString()
        val request1 = ApproveRequest(approver1Id, idempotencyKey1)

        val result1 = mockMvc.post("/api/approvals/$approvalId/steps/$step1Id/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request1)
        }.andExpect {
            status { isOk() }
            jsonPath("$.approvalStatus") { value("IN_PROGRESS") }
            jsonPath("$.activeStepId") { value(step2Id.toString()) }
            jsonPath("$.activeStepStatus") { value("ACTIVE") }
            jsonPath("$.activeStepOrder") { value(2) }
        }.andReturn()

        val response1 = objectMapper.readValue(
            result1.response.contentAsString,
            ApproveResponse::class.java
        )

        // Then: Step 1 approved, Step 2 activated
        assertThat(response1.approvalStatus).isEqualTo(ApprovalStatus.IN_PROGRESS)
        assertThat(response1.activeStepId).isEqualTo(step2Id)
        assertThat(actionLogRepository.count()).isEqualTo(1)
        assertThat(outboxEventRepository.count()).isEqualTo(1)

        // When: Approve step 2 (final step)
        val idempotencyKey2 = UUID.randomUUID().toString()
        val request2 = ApproveRequest(approver2Id, idempotencyKey2)

        val result2 = mockMvc.post("/api/approvals/$approvalId/steps/$step2Id/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request2)
        }.andExpect {
            status { isOk() }
            jsonPath("$.approvalStatus") { value("APPROVED") }
            jsonPath("$.activeStepId") { doesNotExist() }
        }.andReturn()

        val response2 = objectMapper.readValue(
            result2.response.contentAsString,
            ApproveResponse::class.java
        )

        // Then: Approval completed
        assertThat(response2.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)
        assertThat(response2.activeStepId).isNull()
        assertThat(actionLogRepository.count()).isEqualTo(2)
        assertThat(outboxEventRepository.count()).isEqualTo(2)
    }

    @Test
    fun `approve idempotency - duplicate request returns same result`() {
        // Given: 1-step approval
        val approvalId = UUID.randomUUID()
        val stepId = UUID.randomUUID()
        val approverId = UUID.randomUUID()

        val step = ApprovalStep(stepId, 1, approverId)
        val approval = Approval(approvalId, steps = mutableListOf(step))
        approval.startApprovalProcess()

        approvalRepository.save(approval)

        val idempotencyKey = UUID.randomUUID().toString()
        val request = ApproveRequest(approverId, idempotencyKey)

        // When: First approval request
        val result1 = mockMvc.post("/api/approvals/$approvalId/steps/$stepId/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val response1 = objectMapper.readValue(
            result1.response.contentAsString,
            ApproveResponse::class.java
        )

        // When: Duplicate request with same idempotencyKey
        val result2 = mockMvc.post("/api/approvals/$approvalId/steps/$stepId/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
        }.andReturn()

        val response2 = objectMapper.readValue(
            result2.response.contentAsString,
            ApproveResponse::class.java
        )

        // Then: Both responses are identical
        assertThat(response1.approvalId).isEqualTo(response2.approvalId)
        assertThat(response1.approvalStatus).isEqualTo(response2.approvalStatus)
        assertThat(response1.approvalStatus).isEqualTo(ApprovalStatus.APPROVED)

        // Action log recorded only once
        assertThat(actionLogRepository.count()).isEqualTo(1)

        // Outbox event created only once
        assertThat(outboxEventRepository.count()).isEqualTo(1)
    }

    @Test
    fun `approve fail - when step not active`() {
        // Given: 2-step approval with step 1 active
        val approvalId = UUID.randomUUID()
        val step1Id = UUID.randomUUID()
        val step2Id = UUID.randomUUID()
        val approver1Id = UUID.randomUUID()
        val approver2Id = UUID.randomUUID()

        val step1 = ApprovalStep(step1Id, 1, approver1Id)
        val step2 = ApprovalStep(step2Id, 2, approver2Id)
        val approval = Approval(approvalId, steps = mutableListOf(step1, step2))
        approval.startApprovalProcess()

        approvalRepository.save(approval)

        // When: Try to approve step 2 (not active yet)
        val idempotencyKey = UUID.randomUUID().toString()
        val request = ApproveRequest(approver2Id, idempotencyKey)

        mockMvc.post("/api/approvals/$approvalId/steps/$step2Id/approve") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("Only ACTIVE step can be approved") }
        }
    }
}
