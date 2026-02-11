package github.lms.approval.api

import github.lms.approval.application.ApprovalService
import github.lms.approval.application.ApproveCommand
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/approvals")
class ApprovalController(
    private val approvalService: ApprovalService
) {

    @PostMapping("/{approvalId}/steps/{stepId}/approve")
    fun approve(
        @PathVariable approvalId: UUID,
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: ApproveRequest
    ): ResponseEntity<ApproveResponse> {
        val command = ApproveCommand(
            approvalId = approvalId,
            stepId = stepId,
            approverId = request.approverId!!,
            idempotencyKey = request.idempotencyKey!!
        )

        val result = approvalService.approve(command)

        val response = ApproveResponse(
            approvalId = result.approvalId,
            approvalStatus = result.approvalStatus,
            version = result.version,
            activeStepId = result.activeStepId,
            activeStepStatus = result.activeStepStatus,
            activeStepOrder = result.activeStepOrder
        )

        return ResponseEntity.ok(response)
    }
}
