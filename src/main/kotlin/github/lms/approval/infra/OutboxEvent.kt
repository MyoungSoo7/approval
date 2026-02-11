package github.lms.approval.infra

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "outbox_events")
class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, length = 100)
    val aggregateType: String,

    @Column(nullable = false)
    val aggregateId: UUID,

    @Column(nullable = false, length = 100)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(nullable = false, length = 50)
    val status: String = "PENDING",

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    protected constructor() : this(
        null,
        "",
        UUID.randomUUID(),
        "",
        ""
    )
}
