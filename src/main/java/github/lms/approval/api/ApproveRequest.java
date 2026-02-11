package github.lms.approval.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApproveRequest(
        @NotNull(message = "approverId is required")
        UUID approverId,

        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey
) {}
