package br.com.soat.quote.exception

import br.com.soat.shared.exception.ApplicationException
import java.util.UUID

class InvalidApprovalTokenException(tokenId: UUID) :
    ApplicationException("QTE-001", "Invalid or expired approval token: $tokenId")

class QuoteNotFoundException(orderId: UUID) :
    ApplicationException("QTE-002", "Quote not found for order: $orderId")
