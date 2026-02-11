package github.lms.approval.api;

import github.lms.approval.application.ApprovalResult;
import github.lms.approval.application.ApprovalService;
import github.lms.approval.application.ApproveCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{approvalId}/steps/{stepId}/approve")
    public ResponseEntity<ApproveResponse> approve(
            @PathVariable UUID approvalId,
            @PathVariable UUID stepId,
            @Valid @RequestBody ApproveRequest request) {

        ApproveCommand command = new ApproveCommand(
                approvalId,
                stepId,
                request.approverId(),
                request.idempotencyKey()
        );

        ApprovalResult result = approvalService.approve(command);

        ApproveResponse response = new ApproveResponse(
                result.approvalId(),
                result.approvalStatus(),
                result.version(),
                result.activeStepId(),
                result.activeStepStatus(),
                result.activeStepOrder()
        );

        return ResponseEntity.ok(response);
    }
}
