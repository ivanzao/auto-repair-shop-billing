package br.com.soat.quote.repository

import br.com.soat.quote.QuoteApprovalToken
import java.util.UUID

interface QuoteApprovalTokenRepository {
    fun save(token: QuoteApprovalToken): QuoteApprovalToken
    fun findById(id: UUID): QuoteApprovalToken?
    fun update(token: QuoteApprovalToken): QuoteApprovalToken
}
