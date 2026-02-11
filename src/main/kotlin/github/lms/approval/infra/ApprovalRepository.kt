package github.lms.approval.infra

import github.lms.approval.domain.Approval
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ApprovalRepository : JpaRepository<Approval, UUID>
