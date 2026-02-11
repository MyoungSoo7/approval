package github.lms.approval.application

import java.util.*

data class ApproveCommand(
    val approvalId: UUID,
    val stepId: UUID,
    val approverId: UUID,
    val idempotencyKey: String
) {
    init {
        require(idempotencyKey.isNotBlank()) { "idempotencyKey is required" }
    }
}
