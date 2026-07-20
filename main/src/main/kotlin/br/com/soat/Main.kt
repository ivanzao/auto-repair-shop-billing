package br.com.soat

import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.quote.QuoteApprovalTokenPostgresRepository
import br.com.soat.quote.QuotePostgresRepository
import br.com.soat.payment.PaymentProviderPort
import br.com.soat.payment.PaymentUseCase
import br.com.soat.payment.WebhookSignatureValidator
import br.com.soat.quote.QuoteApprovalUseCase
import br.com.soat.quote.QuoteListenerUseCase
import br.com.soat.quote.repository.QuoteApprovalTokenRepository
import br.com.soat.quote.repository.QuoteRepository
import br.com.soat.payment.FakePaymentProvider
import br.com.soat.payment.MercadoPagoPaymentAdapter
import br.com.soat.payment.MercadoPagoSignatureValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import br.com.soat.config.prometheusMeterRegistry
import br.com.soat.consumer.InboundEventConsumer
import br.com.soat.consumer.InboundEventHandler
import br.com.soat.consumer.MessageQueue
import br.com.soat.consumer.SqsClient
import br.com.soat.consumer.handler.ExecutionFailedHandler
import br.com.soat.consumer.handler.SuppliesReservedHandler
import br.com.soat.event.EventPublisher
import br.com.soat.event.OutboxEventPostgresRepository
import br.com.soat.event.repository.OutboxRepository
import br.com.soat.idempotency.IdempotencyPostgresRepository
import br.com.soat.producer.EventEnvelopeSerializer
import br.com.soat.producer.OutboxRelay
import br.com.soat.producer.SnsClient
import br.com.soat.producer.SnsEventPublisher
import br.com.soat.scheduler.ScheduledTask
import br.com.soat.scheduler.ScheduledTaskRunner
import br.com.soat.scheduler.task.OutboxRelayTask
import br.com.soat.shared.repository.IdempotencyRepository
import br.com.soat.shared.repository.RepositoryTransactionHandler
import br.com.soat.transaction.PostgresTransactionHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("br.com.soat.MainKt")

fun main() {
    logger.info("Starting auto-repair-shop-billing application")

    val koinApplication = startKoin {
        modules(applicationModule)
    }

    val config = koinApplication.koin.get<Config>()

    val dataSource = connectToDatabase(config)

    koinApplication.koin.get<InboundEventConsumer>().start()
    logger.info("Inbound saga consumer started")

    koinApplication.koin.get<ScheduledTaskRunner>().start(dataSource)
    logger.info("ScheduledTaskRunner started")

    KtorHttpServer(
        koin = koinApplication.koin,
        port = config.getInt("server.port"),
        wait = true,
    ).start()
}

val applicationModule = module {
    single<Config> { Config.fromClasspath("application.yaml") }
    single<Clock> { Clock.systemUTC() }
    single<CoroutineDispatcher> { Dispatchers.IO }
    single<ObjectMapper> { jacksonObjectMapper().registerModule(JavaTimeModule()) }

    single<PrometheusMeterRegistry> { prometheusMeterRegistry() }
    single<MeterRegistry> { get<PrometheusMeterRegistry>() }

    single {
        val cfg = get<Config>()
        SnsClient(
            topicArn = cfg.getString("sns.topic.arn"),
            region = cfg.getStringOrNull("aws.region") ?: "us-east-1",
            endpointOverride = cfg.getStringOrNull("aws.endpoint"),
            accessKeyId = cfg.getStringOrNull("aws.accessKeyId"),
            secretAccessKey = cfg.getStringOrNull("aws.secretAccessKey"),
        )
    }

    single<OutboxRepository> { OutboxEventPostgresRepository() }
    single<IdempotencyRepository> { IdempotencyPostgresRepository() }
    single<RepositoryTransactionHandler> { PostgresTransactionHandler() }

    single { EventEnvelopeSerializer(get()) }
    single<EventPublisher> { SnsEventPublisher(get(), get<SnsClient>(), get()) }
    single { OutboxRelay(get(), get()) }

    single<QuoteRepository> { QuotePostgresRepository(get()) }
    single<QuoteApprovalTokenRepository> { QuoteApprovalTokenPostgresRepository() }
    single {
        val cfg = get<Config>()
        QuoteListenerUseCase(
            quoteRepository = get(),
            tokenRepository = get(),
            outbox = get(),
            eventPublisher = get(),
            idempotency = get(),
            tx = get(),
            baseUrl = cfg.getString("app.base_url"),
            approvalTtlDays = cfg.getStringOrNull("quote.approval.ttl_days")?.toLong() ?: 5L,
        )
    }
    single { SuppliesReservedHandler(get()) } bind InboundEventHandler::class

    single { QuoteApprovalUseCase(get(), get(), get(), get(), get(), get()) }
    single { PaymentUseCase(get(), get(), get(), get(), get()) }
    single { ExecutionFailedHandler(get()) } bind InboundEventHandler::class
    single<WebhookSignatureValidator> {
        MercadoPagoSignatureValidator(get<Config>().getStringOrNull("mercadopago.webhook_secret") ?: "")
    }

    single { HttpClient(CIO) }
    single<PaymentProviderPort> {
        val cfg = get<Config>()
        val token = cfg.getStringOrNull("mercadopago.access_token")
        if (token.isNullOrBlank()) {
            FakePaymentProvider(cfg.getString("app.base_url"))
        } else {
            MercadoPagoPaymentAdapter(
                client = get(),
                apiUrl = cfg.getString("mercadopago.api_url"),
                accessToken = token,
                backUrlBase = cfg.getString("app.base_url"),
                mapper = get(),
            )
        }
    }

    single<MessageQueue> {
        val cfg = get<Config>()
        SqsClient(
            queueUrl = cfg.getString("sqs.queue.url"),
            region = cfg.getStringOrNull("aws.region") ?: "us-east-1",
            endpointOverride = cfg.getStringOrNull("aws.endpoint"),
            accessKeyId = cfg.getStringOrNull("aws.accessKeyId"),
            secretAccessKey = cfg.getStringOrNull("aws.secretAccessKey"),
        )
    }

    single { InboundEventConsumer(get(), getAll<InboundEventHandler>(), get(), get()) }

    single { OutboxRelayTask(get()) } bind ScheduledTask::class
    single { ScheduledTaskRunner(getAll()) }
}
