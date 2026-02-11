package github.lms.approval.infra

import github.lms.approval.domain.ApprovalActionLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ApprovalActionLogRepository : JpaRepository<ApprovalActionLog, UUID> {
    fun findByApprovalIdAndStepIdAndApproverIdAndIdempotencyKey(
        approvalId: UUID,
        stepId: UUID,
        approverId: UUID,
        idempotencyKey: String
    ): ApprovalActionLog?
}
