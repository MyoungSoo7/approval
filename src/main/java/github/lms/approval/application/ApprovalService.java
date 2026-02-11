package github.lms.approval.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.lms.approval.domain.*;
import github.lms.approval.infra.ApprovalActionLogRepository;
import github.lms.approval.infra.ApprovalRepository;
import github.lms.approval.infra.OutboxEvent;
import github.lms.approval.infra.OutboxEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final ApprovalActionLogRepository actionLogRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ApprovalService(
            ApprovalRepository approvalRepository,
            ApprovalActionLogRepository actionLogRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.approvalRepository = approvalRepository;
        this.actionLogRepository = actionLogRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ApprovalResult approve(ApproveCommand command) {
        // 1. Check idempotency - if already processed, return existing result
        Optional<ApprovalActionLog> existingAction = actionLogRepository
                .findByApprovalIdAndStepIdAndApproverIdAndIdempotencyKey(
                        command.approvalId(),
                        command.stepId(),
                        command.approverId(),
                        command.idempotencyKey()
                );

        if (existingAction.isPresent()) {
            // Already processed - return current state
            Approval approval = approvalRepository.findById(command.approvalId())
                    .orElseThrow(() -> new IllegalArgumentException("Approval not found"));
            return buildResult(approval);
        }

        // 2. Load approval with optimistic lock
        Approval approval = approvalRepository.findById(command.approvalId())
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + command.approvalId()));

        // 3. Execute state transition
        ApprovalStep approvedStep = approval.approveStep(command.stepId(), command.approverId());

        // 4. Record action log for idempotency (unique constraint will prevent duplicates)
        ApprovalActionLog actionLog = new ApprovalActionLog(
                command.approvalId(),
                command.stepId(),
                command.approverId(),
                command.idempotencyKey(),
                "APPROVE"
        );

        try {
            actionLogRepository.save(actionLog);
        } catch (DataIntegrityViolationException e) {
            // Race condition - another transaction already processed this
            // Reload and return current state
            approval = approvalRepository.findById(command.approvalId())
                    .orElseThrow(() -> new IllegalArgumentException("Approval not found"));
            return buildResult(approval);
        }

        // 5. Save approval (with version increment for optimistic lock)
        approvalRepository.save(approval);

        // 6. If step approved, save outbox event
        if (approvedStep.getStatus() == StepStatus.APPROVED) {
            saveOutboxEvent(approval, approvedStep);
        }

        // 7. Return result
        return buildResult(approval);
    }

    private void saveOutboxEvent(Approval approval, ApprovalStep approvedStep) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("approvalId", approval.getId().toString());
        payload.put("stepId", approvedStep.getId().toString());
        payload.put("stepOrder", approvedStep.getStepOrder());
        payload.put("approverId", approvedStep.getApproverId().toString());
        payload.put("approvedAt", approvedStep.getApprovedAt().toString());
        payload.put("approvalStatus", approval.getStatus().name());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }

        OutboxEvent event = new OutboxEvent(
                "Approval",
                approval.getId(),
                "ApprovalStepApproved",
                payloadJson
        );

        outboxEventRepository.save(event);

        // TODO: Outbox polling/relay mechanism would publish this event to message broker
        // For now, events remain in PENDING status in the database
    }

    private ApprovalResult buildResult(Approval approval) {
        ApprovalStep activeStep = approval.getActiveStep();
        return new ApprovalResult(
                approval.getId(),
                approval.getStatus(),
                approval.getVersion(),
                activeStep != null ? activeStep.getId() : null,
                activeStep != null ? activeStep.getStatus() : null,
                activeStep != null ? activeStep.getStepOrder() : null
        );
    }
}
