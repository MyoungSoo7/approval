package github.lms.approval.infra

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID>
