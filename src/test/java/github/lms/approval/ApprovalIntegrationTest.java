package github.lms.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.lms.approval.api.ApproveRequest;
import github.lms.approval.api.ApproveResponse;
import github.lms.approval.domain.Approval;
import github.lms.approval.domain.ApprovalStatus;
import github.lms.approval.domain.ApprovalStep;
import github.lms.approval.domain.StepStatus;
import github.lms.approval.infra.ApprovalActionLogRepository;
import github.lms.approval.infra.ApprovalRepository;
import github.lms.approval.infra.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private ApprovalActionLogRepository actionLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        actionLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
        approvalRepository.deleteAll();
    }

    @Test
    void approve_success_twoStepApproval() throws Exception {
        // Given: 2-step approval in DRAFT status
        UUID approvalId = UUID.randomUUID();
        UUID step1Id = UUID.randomUUID();
        UUID step2Id = UUID.randomUUID();
        UUID approver1Id = UUID.randomUUID();
        UUID approver2Id = UUID.randomUUID();

        ApprovalStep step1 = new ApprovalStep(step1Id, 1, approver1Id);
        ApprovalStep step2 = new ApprovalStep(step2Id, 2, approver2Id);
        Approval approval = new Approval(approvalId, List.of(step1, step2));
        approval.startApprovalProcess();

        approvalRepository.save(approval);

        // When: Approve step 1
        String idempotencyKey1 = UUID.randomUUID().toString();
        ApproveRequest request1 = new ApproveRequest(approver1Id, idempotencyKey1);

        MvcResult result1 = mockMvc.perform(post("/api/approvals/{approvalId}/steps/{stepId}/approve", approvalId, step1Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.activeStepId").value(step2Id.toString()))
                .andExpect(jsonPath("$.activeStepStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.activeStepOrder").value(2))
                .andReturn();

        ApproveResponse response1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(),
                ApproveResponse.class
        );

        // Then: Step 1 approved, Step 2 activated
        assertThat(response1.approvalStatus()).isEqualTo(ApprovalStatus.IN_PROGRESS);
        assertThat(response1.activeStepId()).isEqualTo(step2Id);
        assertThat(actionLogRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        // When: Approve step 2 (final step)
        String idempotencyKey2 = UUID.randomUUID().toString();
        ApproveRequest request2 = new ApproveRequest(approver2Id, idempotencyKey2);

        MvcResult result2 = mockMvc.perform(post("/api/approvals/{approvalId}/steps/{stepId}/approve", approvalId, step2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"))
                .andExpect(jsonPath("$.activeStepId").doesNotExist())
                .andReturn();

        ApproveResponse response2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(),
                ApproveResponse.class
        );

        // Then: Approval completed
        assertThat(response2.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response2.activeStepId()).isNull();
        assertThat(actionLogRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    @Test
    void approve_idempotency_duplicateRequestReturnsSameResult() throws Exception {
        // Given: 1-step approval
        UUID approvalId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();

        ApprovalStep step = new ApprovalStep(stepId, 1, approverId);
        Approval approval = new Approval(approvalId, List.of(step));
        approval.startApprovalProcess();

        approvalRepository.save(approval);

        String idempotencyKey = UUID.randomUUID().toString();
        ApproveRequest request = new ApproveRequest(approverId, idempotencyKey);

        // When: First approval request
        MvcResult result1 = mockMvc.perform(post("/api/approvals/{approvalId}/steps/{stepId}/approve", approvalId, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApproveResponse response1 = objectMapper.readValue(
                result1.getResponse().getContentAsString(),
                ApproveResponse.class
        );

        // When: Duplicate request with same idempotencyKey
        MvcResult result2 = mockMvc.perform(post("/api/approvals/{approvalId}/steps/{stepId}/approve", approvalId, stepId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        ApproveResponse response2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(),
                ApproveResponse.class
        );

        // Then: Both responses are identical
        assertThat(response1.approvalId()).isEqualTo(response2.approvalId());
        assertThat(response1.approvalStatus()).isEqualTo(response2.approvalStatus());
        assertThat(response1.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);

        // Action log recorded only once
        assertThat(actionLogRepository.count()).isEqualTo(1);

        // Outbox event created only once
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void approve_fail_whenStepNotActive() throws Exception {
        // Given: 2-step approval with step 1 active
        UUID approvalId = UUID.randomUUID();
        UUID step1Id = UUID.randomUUID();
        UUID step2Id = UUID.randomUUID();
        UUID approver1Id = UUID.randomUUID();
        UUID approver2Id = UUID.randomUUID();

        ApprovalStep step1 = new ApprovalStep(step1Id, 1, approver1Id);
        ApprovalStep step2 = new ApprovalStep(step2Id, 2, approver2Id);
        Approval approval = new Approval(approvalId, List.of(step1, step2));
        approval.startApprovalProcess();

        approvalRepository.save(approval);

        // When: Try to approve step 2 (not active yet)
        String idempotencyKey = UUID.randomUUID().toString();
        ApproveRequest request = new ApproveRequest(approver2Id, idempotencyKey);

        mockMvc.perform(post("/api/approvals/{approvalId}/steps/{stepId}/approve", approvalId, step2Id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only ACTIVE step can be approved"));
    }
}
