package github.lms.approval.infra;

import github.lms.approval.domain.ApprovalActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalActionLogRepository extends JpaRepository<ApprovalActionLog, UUID> {
    Optional<ApprovalActionLog> findByApprovalIdAndStepIdAndApproverIdAndIdempotencyKey(
            UUID approvalId, UUID stepId, UUID approverId, String idempotencyKey);
}
