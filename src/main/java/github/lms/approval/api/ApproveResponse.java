package github.lms.approval.api;

import github.lms.approval.domain.ApprovalStatus;
import github.lms.approval.domain.StepStatus;

import java.util.UUID;

public record ApproveResponse(
        UUID approvalId,
        ApprovalStatus approvalStatus,
        Long version,
        UUID activeStepId,
        StepStatus activeStepStatus,
        Integer activeStepOrder
) {}
