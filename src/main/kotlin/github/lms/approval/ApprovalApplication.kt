package github.lms.approval

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ApprovalApplication

fun main(args: Array<String>) {
    runApplication<ApprovalApplication>(*args)
}
