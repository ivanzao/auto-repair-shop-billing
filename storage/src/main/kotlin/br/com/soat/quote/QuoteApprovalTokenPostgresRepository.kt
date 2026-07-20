package br.com.soat.quote

import br.com.soat.quote.QuoteApprovalToken
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import java.util.UUID
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class QuoteApprovalTokenPostgresRepository : QuoteApprovalTokenRepository {

    override fun save(token: QuoteApprovalToken): QuoteApprovalToken = transaction {
        QuoteApprovalTokens.insert {
            it[id] = token.id
            it[orderId] = token.orderId
            it[expiresAt] = token.expiresAt.toKotlinLocalDateTime()
            it[usedAt] = token.usedAt?.toKotlinLocalDateTime()
            it[createdAt] = token.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = token.modifiedAt.toKotlinLocalDateTime()
            it[version] = token.version
        }.resultedValues?.singleOrNull()?.toQuoteApprovalToken()
            ?: throw IllegalStateException("An error occurred while saving QuoteApprovalToken")
    }

    override fun findById(id: UUID): QuoteApprovalToken? = transaction {
        QuoteApprovalTokens.selectAll()
            .where { QuoteApprovalTokens.id eq id }
            .limit(1)
            .firstOrNull()
            ?.toQuoteApprovalToken()
    }

    override fun update(token: QuoteApprovalToken): QuoteApprovalToken = transaction {
        QuoteApprovalTokens.update({
            (QuoteApprovalTokens.id eq token.id) and (QuoteApprovalTokens.version eq token.version)
        }) {
            it[expiresAt] = token.expiresAt.toKotlinLocalDateTime()
            it[usedAt] = token.usedAt?.toKotlinLocalDateTime()
            it[modifiedAt] = token.modifiedAt.toKotlinLocalDateTime()
            it[version] = token.version + 1
        }

        findById(token.id) ?: throw IllegalStateException("An error occurred while updating QuoteApprovalToken")
    }
}
