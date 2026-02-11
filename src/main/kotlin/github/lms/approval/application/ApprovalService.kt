package github.lms.approval.application

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import github.lms.approval.domain.*
import github.lms.approval.infra.ApprovalActionLogRepository
import github.lms.approval.infra.ApprovalRepository
import github.lms.approval.infra.OutboxEvent
import github.lms.approval.infra.OutboxEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ApprovalService(
    private val approvalRepository: ApprovalRepository,
    private val actionLogRepository: ApprovalActionLogRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun approve(command: ApproveCommand): ApprovalResult {
        // 1. Check idempotency - if already processed, return existing result
        actionLogRepository.findByApprovalIdAndStepIdAndApproverIdAndIdempotencyKey(
            command.approvalId,
            command.stepId,
            command.approverId,
            command.idempotencyKey
        )?.let {
            // Already processed - return current state
            val approval = approvalRepository.findById(command.approvalId)
                .orElseThrow { IllegalArgumentException("Approval not found") }
            return buildResult(approval)
        }

        // 2. Load approval with optimistic lock
        val approval = approvalRepository.findById(command.approvalId)
            .orElseThrow { IllegalArgumentException("Approval not found: ${command.approvalId}") }

        // 3. Execute state transition
        val approvedStep = approval.approveStep(command.stepId, command.approverId)

        // 4. Record action log for idempotency (unique constraint will prevent duplicates)
        val actionLog = ApprovalActionLog(
            approvalId = command.approvalId,
            stepId = command.stepId,
            approverId = command.approverId,
            idempotencyKey = command.idempotencyKey,
            actionType = "APPROVE"
        )

        try {
            actionLogRepository.save(actionLog)
        } catch (e: DataIntegrityViolationException) {
            // Race condition - another transaction already processed this
            // Reload and return current state
            val reloadedApproval = approvalRepository.findById(command.approvalId)
                .orElseThrow { IllegalArgumentException("Approval not found") }
            return buildResult(reloadedApproval)
        }

        // 5. Save approval (with version increment for optimistic lock)
        approvalRepository.save(approval)

        // 6. If step approved, save outbox event
        if (approvedStep.status == StepStatus.APPROVED) {
            saveOutboxEvent(approval, approvedStep)
        }

        // 7. Return result
        return buildResult(approval)
    }

    private fun saveOutboxEvent(approval: Approval, approvedStep: ApprovalStep) {
        val payload = mapOf(
            "approvalId" to approval.id.toString(),
            "stepId" to approvedStep.id.toString(),
            "stepOrder" to approvedStep.stepOrder,
            "approverId" to approvedStep.approverId.toString(),
            "approvedAt" to approvedStep.approvedAt.toString(),
            "approvalStatus" to approval.status.name
        )

        val payloadJson = try {
            objectMapper.writeValueAsString(payload)
        } catch (e: JsonProcessingException) {
            throw RuntimeException("Failed to serialize event payload", e)
        }

        val event = OutboxEvent(
            aggregateType = "Approval",
            aggregateId = approval.id,
            eventType = "ApprovalStepApproved",
            payload = payloadJson
        )

        outboxEventRepository.save(event)

        // TODO: Outbox polling/relay mechanism would publish this event to message broker
        // For now, events remain in PENDING status in the database
    }

    private fun buildResult(approval: Approval): ApprovalResult {
        val activeStep = approval.getActiveStep()
        return ApprovalResult(
            approvalId = approval.id,
            approvalStatus = approval.status,
            version = approval.version,
            activeStepId = activeStep?.id,
            activeStepStatus = activeStep?.status,
            activeStepOrder = activeStep?.stepOrder
        )
    }
}
