package github.lms.approval.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(
    name = "approval_action_logs",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_action_idempotency",
            columnNames = ["approval_id", "step_id", "approver_id", "idempotency_key"]
        )
    ]
)
class ApprovalActionLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false)
    val approvalId: UUID,

    @Column(nullable = false)
    val stepId: UUID,

    @Column(nullable = false)
    val approverId: UUID,

    @Column(nullable = false, length = 255)
    val idempotencyKey: String,

    @Column(nullable = false, length = 50)
    val actionType: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    protected constructor() : this(
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "",
        ""
    )
}
