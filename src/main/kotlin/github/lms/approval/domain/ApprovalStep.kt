package github.lms.approval.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "approval_steps")
class ApprovalStep(
    @Id
    val id: UUID,

    @Column(nullable = false)
    val stepOrder: Int,

    @Column(nullable = false)
    val assigneeId: UUID,

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var status: StepStatus = StepStatus.PENDING,

    var approverId: UUID? = null,

    var approvedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id", nullable = false)
    var approval: Approval? = null

    protected constructor() : this(UUID.randomUUID(), 0, UUID.randomUUID())

    fun activate() {
        require(status == StepStatus.PENDING) {
            "Only PENDING step can be activated"
        }
        status = StepStatus.ACTIVE
        updatedAt = LocalDateTime.now()
    }

    fun approve(approverId: UUID) {
        require(status == StepStatus.ACTIVE) {
            "Only ACTIVE step can be approved"
        }
        status = StepStatus.APPROVED
        this.approverId = approverId
        approvedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }
}
