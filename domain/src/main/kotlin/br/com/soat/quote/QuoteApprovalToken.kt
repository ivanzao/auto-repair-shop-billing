package br.com.soat.quote

import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID
import java.util.UUID.randomUUID

data class QuoteApprovalToken(
    val id: UUID = randomUUID(),
    val orderId: UUID,
    val expiresAt: LocalDateTime,
    val usedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = now(),
    val modifiedAt: LocalDateTime = now(),
    val version: Int = 0,
) {
    fun isValid(): Boolean = usedAt == null && now().isBefore(expiresAt)

    fun markAsUsed(): QuoteApprovalToken = copy(usedAt = now(), modifiedAt = now())
}
