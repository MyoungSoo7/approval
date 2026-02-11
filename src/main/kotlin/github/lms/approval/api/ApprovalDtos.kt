package github.lms.approval.api

import github.lms.approval.domain.ApprovalStatus
import github.lms.approval.domain.StepStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class ApproveRequest(
    @field:NotNull(message = "approverId is required")
    val approverId: UUID?,

    @field:NotBlank(message = "idempotencyKey is required")
    val idempotencyKey: String?
)

data class ApproveResponse(
    val approvalId: UUID,
    val approvalStatus: ApprovalStatus,
    val version: Long?,
    val activeStepId: UUID?,
    val activeStepStatus: StepStatus?,
    val activeStepOrder: Int?
)
