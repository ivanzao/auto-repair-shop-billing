package br.com.soat.config

import br.com.soat.quote.quoteRoutes
import br.com.soat.payment.webhookRoutes
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.configureRouting(koin: Koin) {
    routing {
        get("/health") {
            call.respondText("OK")
        }
    }

    quoteRoutes(koin)
    webhookRoutes(koin)
}
