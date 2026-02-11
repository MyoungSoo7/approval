package github.lms.approval.application

import github.lms.approval.domain.ApprovalStatus
import github.lms.approval.domain.StepStatus
import java.util.*

data class ApprovalResult(
    val approvalId: UUID,
    val approvalStatus: ApprovalStatus,
    val version: Long?,
    val activeStepId: UUID?,
    val activeStepStatus: StepStatus?,
    val activeStepOrder: Int?
)
