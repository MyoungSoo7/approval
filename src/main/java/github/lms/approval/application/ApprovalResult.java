package github.lms.approval.application;

import github.lms.approval.domain.ApprovalStatus;
import github.lms.approval.domain.StepStatus;

import java.util.UUID;

public record ApprovalResult(
        UUID approvalId,
        ApprovalStatus approvalStatus,
        Long version,
        UUID activeStepId,
        StepStatus activeStepStatus,
        Integer activeStepOrder
) {}
