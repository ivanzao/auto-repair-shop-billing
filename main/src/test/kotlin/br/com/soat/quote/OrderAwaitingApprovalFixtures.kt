package br.com.soat.quote

import java.time.Instant
import java.util.UUID

val CONTRACT_ORDER_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
val CONTRACT_RESERVATION_ID: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")

fun orderAwaitingApprovalEnvelope(eventId: UUID = UUID.randomUUID()): String =
    """
    {
      "eventId":"$eventId",
      "eventType":"OrderAwaitingApproval",
      "eventVersion":1,
      "occurredAt":"${Instant.now()}",
      "payload":{
        "orderId": "11111111-1111-1111-1111-111111111111",
        "reservationId": "44444444-4444-4444-4444-444444444444",
        "customer": {
          "name": "Maria Silva",
          "email": "maria@exemplo.com"
        },
        "services": [
          { "id": "55555555-5555-5555-5555-555555555555", "name": "Troca de oleo", "price": 100.00 }
        ],
        "supplies": [
          { "id": "66666666-6666-6666-6666-666666666666", "name": "Filtro de oleo", "quantity": 2, "unitPrice": 30.00 }
        ],
        "totalAmount": 160.00
      }
    }
    """.trimIndent()
