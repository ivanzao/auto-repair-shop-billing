package br.com.soat.quote

import java.time.Instant
import java.util.UUID

fun suppliesReservedEnvelope(
    orderId: UUID,
    reservationId: UUID,
    customerName: String = "John Doe",
    customerEmail: String = "john@example.com",
    eventId: UUID = UUID.randomUUID(),
): String =
    """
    {
      "eventId":"$eventId",
      "eventType":"SuppliesReserved",
      "eventVersion":1,
      "occurredAt":"${Instant.now()}",
      "payload":{
        "orderId":"$orderId",
        "reservationId":"$reservationId",
        "customer":{"id":"${UUID.randomUUID()}","name":"$customerName","email":"$customerEmail"},
        "services":[{"name":"Oil Change","price":100.00}],
        "supplies":[{"id":"${UUID.randomUUID()}","name":"Oil Filter","quantity":2,"unitPrice":30.00}],
        "totalAmount":160.00
      }
    }
    """.trimIndent()
