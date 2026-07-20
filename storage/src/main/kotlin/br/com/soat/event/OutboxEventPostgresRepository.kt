package br.com.soat.event

import br.com.soat.event.model.DomainEvent
import br.com.soat.event.repository.OutboxRepository
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

@JsonIgnoreProperties("eventId", "eventVersion", "occurredAt", "eventType")
private abstract class DomainEventMixin

class OutboxEventPostgresRepository : OutboxRepository {

    private val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .addMixIn(DomainEvent::class.java, DomainEventMixin::class.java)

    override fun save(event: DomainEvent): OutboxEvent = transaction {
        val row = OutboxEvent(
            eventId = event.eventId,
            eventType = event.eventType,
            eventVersion = event.eventVersion,
            occurredAt = event.occurredAt,
            payload = mapper.writeValueAsString(event),
        )
        Events.insert {
            it[id] = row.eventId
            it[createdAt] = LocalDateTime.ofInstant(row.occurredAt, ZoneOffset.UTC).toKotlinLocalDateTime()
            it[modifiedAt] = LocalDateTime.now().toKotlinLocalDateTime()
            it[version] = row.eventVersion
            it[type] = row.eventType
            it[payload] = row.payload
        }
        row
    }

    override fun findPendingOlderThan(age: Duration, limit: Int): List<OutboxEvent> = transaction {
        val threshold = LocalDateTime.now(ZoneOffset.UTC).minus(age).toKotlinLocalDateTime()
        Events.selectAll()
            .where { Events.createdAt less threshold }
            .orderBy(Events.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                OutboxEvent(
                    eventId = row[Events.id],
                    eventType = row[Events.type],
                    eventVersion = row[Events.version],
                    occurredAt = row[Events.createdAt].toJavaLocalDateTime().toInstant(ZoneOffset.UTC),
                    payload = row[Events.payload],
                )
            }
    }

    override fun delete(eventId: UUID) {
        transaction { Events.deleteWhere { Events.id eq eventId } }
    }
}
