package github.lms.approval.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "approvals")
class Approval(
    @Id
    val id: UUID,

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var status: ApprovalStatus = ApprovalStatus.DRAFT,

    @Version
    var version: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "approval", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    val steps: MutableList<ApprovalStep> = mutableListOf()
) {
    protected constructor() : this(UUID.randomUUID())

    init {
        steps.forEach { it.approval = this }
    }

    fun startApprovalProcess() {
        require(status == ApprovalStatus.DRAFT) {
            "Approval must be in DRAFT status to start"
        }
        require(steps.isNotEmpty()) {
            "Cannot start approval without steps"
        }

        status = ApprovalStatus.IN_PROGRESS
        steps[0].activate()
        updatedAt = LocalDateTime.now()
    }

    fun approveStep(stepId: UUID, approverId: UUID): ApprovalStep {
        val step = findStepById(stepId)

        require(step.status == StepStatus.ACTIVE) {
            "Only ACTIVE step can be approved"
        }

        step.approve(approverId)
        updatedAt = LocalDateTime.now()

        val currentIndex = steps.indexOf(step)
        if (currentIndex == steps.size - 1) {
            // Last step approved -> approval completed
            status = ApprovalStatus.APPROVED
        } else {
            // Activate next step
            steps[currentIndex + 1].activate()
            status = ApprovalStatus.IN_PROGRESS
        }

        return step
    }

    private fun findStepById(stepId: UUID): ApprovalStep =
        steps.find { it.id == stepId }
            ?: throw IllegalArgumentException("Step not found: $stepId")

    fun getActiveStep(): ApprovalStep? =
        steps.find { it.status == StepStatus.ACTIVE }
}
