package github.lms.approval.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_steps")
public class ApprovalStep {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id", nullable = false)
    private Approval approval;

    @Column(nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    private UUID assigneeId;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private UUID approverId;

    private LocalDateTime approvedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected ApprovalStep() {}

    public ApprovalStep(UUID id, Integer stepOrder, UUID assigneeId) {
        this.id = id;
        this.stepOrder = stepOrder;
        this.assigneeId = assigneeId;
        this.status = StepStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void setApproval(Approval approval) {
        this.approval = approval;
    }

    public void activate() {
        if (this.status != StepStatus.PENDING) {
            throw new IllegalStateException("Only PENDING step can be activated");
        }
        this.status = StepStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void approve(UUID approverId) {
        if (this.status != StepStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE step can be approved");
        }
        this.status = StepStatus.APPROVED;
        this.approverId = approverId;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public Approval getApproval() {
        return approval;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public StepStatus getStatus() {
        return status;
    }

    public UUID getApproverId() {
        return approverId;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
