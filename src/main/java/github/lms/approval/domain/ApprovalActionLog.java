package github.lms.approval.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "approval_action_logs",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_action_idempotency",
        columnNames = {"approval_id", "step_id", "approver_id", "idempotency_key"}
    )
)
public class ApprovalActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID approvalId;

    @Column(nullable = false)
    private UUID stepId;

    @Column(nullable = false)
    private UUID approverId;

    @Column(nullable = false, length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 50)
    private String actionType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ApprovalActionLog() {}

    public ApprovalActionLog(UUID approvalId, UUID stepId, UUID approverId, String idempotencyKey, String actionType) {
        this.approvalId = approvalId;
        this.stepId = stepId;
        this.approverId = approverId;
        this.idempotencyKey = idempotencyKey;
        this.actionType = actionType;
        this.createdAt = LocalDateTime.now();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getApprovalId() {
        return approvalId;
    }

    public UUID getStepId() {
        return stepId;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getActionType() {
        return actionType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
