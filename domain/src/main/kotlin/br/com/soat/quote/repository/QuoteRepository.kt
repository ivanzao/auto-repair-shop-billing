package br.com.soat.quote.repository

import br.com.soat.quote.Quote
import java.util.UUID

interface QuoteRepository {
    fun findByOrderId(orderId: UUID): Quote?
    fun save(quote: Quote): Quote
    fun update(quote: Quote): Quote
}
