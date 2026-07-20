package br.com.soat.quote

import br.com.soat.quote.Quote
import br.com.soat.quote.QuoteStatus
import br.com.soat.quote.repository.QuoteRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.UUID
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class QuotePostgresRepository(
    private val mapper: ObjectMapper,
) : QuoteRepository {

    override fun findByOrderId(orderId: UUID): Quote? = transaction {
        Quotes.selectAll()
            .where { Quotes.orderId eq orderId }
            .limit(1)
            .firstOrNull()
            ?.toQuote()
    }

    override fun save(quote: Quote): Quote = transaction {
        Quotes.insert {
            it[orderId] = quote.orderId
            it[reservationId] = quote.reservationId
            it[customerName] = quote.customerName
            it[customerEmail] = quote.customerEmail
            it[lineItems] = mapper.writeValueAsString(quote.lineItems)
            it[totalAmount] = quote.totalAmount
            it[status] = quote.status.name
            it[paymentId] = quote.paymentId
            it[preferenceId] = quote.preferenceId
            it[createdAt] = quote.createdAt.toKotlinLocalDateTime()
            it[modifiedAt] = quote.modifiedAt.toKotlinLocalDateTime()
            it[version] = quote.version
        }.resultedValues?.singleOrNull()?.toQuote()
            ?: throw IllegalStateException("An error occurred while saving Quote")
    }

    override fun update(quote: Quote): Quote = transaction {
        Quotes.update({ (Quotes.orderId eq quote.orderId) and (Quotes.version eq quote.version) }) {
            it[reservationId] = quote.reservationId
            it[customerName] = quote.customerName
            it[customerEmail] = quote.customerEmail
            it[lineItems] = mapper.writeValueAsString(quote.lineItems)
            it[totalAmount] = quote.totalAmount
            it[status] = quote.status.name
            it[paymentId] = quote.paymentId
            it[preferenceId] = quote.preferenceId
            it[modifiedAt] = quote.modifiedAt.toKotlinLocalDateTime()
            it[version] = quote.version + 1
        }

        findByOrderId(quote.orderId) ?: throw IllegalStateException("An error occurred while updating Quote")
    }

    private fun ResultRow.toQuote(): Quote = Quote(
        orderId = this[Quotes.orderId],
        reservationId = this[Quotes.reservationId],
        customerName = this[Quotes.customerName],
        customerEmail = this[Quotes.customerEmail],
        lineItems = mapper.readValue(this[Quotes.lineItems]),
        totalAmount = this[Quotes.totalAmount],
        status = QuoteStatus.valueOf(this[Quotes.status]),
        paymentId = this[Quotes.paymentId],
        preferenceId = this[Quotes.preferenceId],
        createdAt = this[Quotes.createdAt].toJavaLocalDateTime(),
        modifiedAt = this[Quotes.modifiedAt].toJavaLocalDateTime(),
        version = this[Quotes.version],
    )
}
