package github.lms.approval.application;

import java.util.UUID;

public record ApproveCommand(
        UUID approvalId,
        UUID stepId,
        UUID approverId,
        String idempotencyKey
) {
    public ApproveCommand {
        if (approvalId == null) {
            throw new IllegalArgumentException("approvalId is required");
        }
        if (stepId == null) {
            throw new IllegalArgumentException("stepId is required");
        }
        if (approverId == null) {
            throw new IllegalArgumentException("approverId is required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
    }
}
