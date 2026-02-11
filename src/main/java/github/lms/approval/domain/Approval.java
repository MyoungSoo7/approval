package github.lms.approval.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "approval", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<ApprovalStep> steps = new ArrayList<>();

    protected Approval() {}

    public Approval(UUID id, List<ApprovalStep> steps) {
        this.id = id;
        this.status = ApprovalStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.steps = steps;
        steps.forEach(step -> step.setApproval(this));
    }

    public void startApprovalProcess() {
        if (this.status != ApprovalStatus.DRAFT) {
            throw new IllegalStateException("Approval must be in DRAFT status to start");
        }
        if (steps.isEmpty()) {
            throw new IllegalStateException("Cannot start approval without steps");
        }

        this.status = ApprovalStatus.IN_PROGRESS;
        steps.get(0).activate();
        this.updatedAt = LocalDateTime.now();
    }

    public ApprovalStep approveStep(UUID stepId, UUID approverId) {
        ApprovalStep step = findStepById(stepId);

        if (step.getStatus() != StepStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE step can be approved");
        }

        step.approve(approverId);
        this.updatedAt = LocalDateTime.now();

        // Check if this is the last step
        int currentIndex = steps.indexOf(step);
        if (currentIndex == steps.size() - 1) {
            // Last step approved -> approval completed
            this.status = ApprovalStatus.APPROVED;
        } else {
            // Activate next step
            ApprovalStep nextStep = steps.get(currentIndex + 1);
            nextStep.activate();
            this.status = ApprovalStatus.IN_PROGRESS;
        }

        return step;
    }

    private ApprovalStep findStepById(UUID stepId) {
        return steps.stream()
                .filter(s -> s.getId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step not found: " + stepId));
    }

    public ApprovalStep getActiveStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == StepStatus.ACTIVE)
                .findFirst()
                .orElse(null);
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ApprovalStep> getSteps() {
        return steps;
    }
}
