package br.com.soat.quote

import br.com.soat.quote.QuoteApprovalUseCase
import br.com.soat.shared.getUUIDQueryParameter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.core.Koin

fun Application.quoteRoutes(koin: Koin) {
    val quoteApprovalUseCase = koin.inject<QuoteApprovalUseCase>().value

    routing {
        route("/v1") {
            get("/quotes/approve") {
                val token = call.getUUIDQueryParameter("token")
                val initPoint = quoteApprovalUseCase.approve(token)
                call.respondRedirect(initPoint, permanent = false)
            }

            get("/quotes/decline") {
                val token = call.getUUIDQueryParameter("token")
                quoteApprovalUseCase.decline(token)
                call.respond(HttpStatusCode.OK, mapOf("status" to "declined"))
            }
        }
    }
}
