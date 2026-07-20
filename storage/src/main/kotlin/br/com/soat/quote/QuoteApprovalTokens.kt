package br.com.soat.quote

import br.com.soat.quote.QuoteApprovalToken
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.datetime

object QuoteApprovalTokens : Table("quote_approval_tokens") {
    val id = uuid("id")
    val orderId = uuid("order_id")
    val expiresAt = datetime("expires_at")
    val usedAt = datetime("used_at").nullable()
    val createdAt = datetime("created_at")
    val modifiedAt = datetime("modified_at")
    val version = integer("version").default(0)

    init {
        PrimaryKey(id)
    }
}

fun ResultRow.toQuoteApprovalToken(): QuoteApprovalToken = QuoteApprovalToken(
    id = this[QuoteApprovalTokens.id],
    orderId = this[QuoteApprovalTokens.orderId],
    expiresAt = this[QuoteApprovalTokens.expiresAt].toJavaLocalDateTime(),
    usedAt = this[QuoteApprovalTokens.usedAt]?.toJavaLocalDateTime(),
    createdAt = this[QuoteApprovalTokens.createdAt].toJavaLocalDateTime(),
    modifiedAt = this[QuoteApprovalTokens.modifiedAt].toJavaLocalDateTime(),
    version = this[QuoteApprovalTokens.version],
)
