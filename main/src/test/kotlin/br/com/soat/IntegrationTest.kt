package br.com.soat

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import aws.sdk.kotlin.services.sns.model.CreateTopicRequest
import aws.sdk.kotlin.services.sns.model.SubscribeRequest
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.sdk.kotlin.services.sqs.model.CreateQueueRequest
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.GetQueueAttributesRequest
import aws.sdk.kotlin.services.sqs.model.MessageAttributeValue
import aws.sdk.kotlin.services.sqs.model.PurgeQueueRequest
import aws.sdk.kotlin.services.sqs.model.QueueAttributeName
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.sdk.kotlin.services.sqs.model.SendMessageRequest
import aws.smithy.kotlin.runtime.net.url.Url
import br.com.soat.config.Config
import br.com.soat.config.fromClasspath
import br.com.soat.scheduler.ScheduledTaskRunner
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.ServerSocket
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS
import org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS
import org.testcontainers.utility.DockerImageName

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTest {

    protected var serverPort = ServerSocket(0).use { it.localPort }
    protected val postgresContainer = PostgreSQLContainer("postgres:18.1").withReuse(true)!!
    protected val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.7"))
        .withServices(SNS, SQS)
        .withReuse(true)

    lateinit var koinApplication: KoinApplication
    lateinit var server: KtorHttpServer
    lateinit var http: IntegrationTestHttpClient
    lateinit var sqsClient: SqsClient

    lateinit var sagaQueueUrl: String

    lateinit var subscriberQueueUrl: String

    @BeforeEach
    fun cleanState() {
        transaction {
            exec(
                """
                DO ${'$'}${'$'} DECLARE
                    r RECORD;
                BEGIN
                    FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname = 'public') LOOP
                        EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
                    END LOOP;
                END ${'$'}${'$'};
                """.trimIndent(),
            )
        }
        runBlocking {
            sqsClient.purgeQueue(PurgeQueueRequest { queueUrl = sagaQueueUrl })
            sqsClient.purgeQueue(PurgeQueueRequest { queueUrl = subscriberQueueUrl })
        }
    }

    @BeforeAll
    open fun setup() {
        postgresContainer.start()
        localstack.start()

        val endpoint = localstack.getEndpointOverride(SNS).toString()
        val (topicArn, subUrl, sagaUrl) = provisionMessaging(endpoint)
        subscriberQueueUrl = subUrl
        sagaQueueUrl = sagaUrl

        val config = Config.fromClasspath("application-test.yaml").apply {
            put("database.url", postgresContainer.jdbcUrl)
            put("database.username", postgresContainer.username)
            put("database.password", postgresContainer.password)
            put("database.driverClassName", postgresContainer.driverClassName)
            put("server.port", serverPort)
            put("sns.topic.arn", topicArn)
            put("sqs.queue.url", sagaUrl)
            put("aws.endpoint", endpoint)
            put("aws.region", localstack.region)
            put("aws.accessKeyId", localstack.accessKey)
            put("aws.secretAccessKey", localstack.secretKey)
        }

        val dataSource = connectToDatabase(config)

        koinApplication = startKoin { modules(applicationModule, testModule(config)) }

        http = IntegrationTestHttpClient(serverPort)

        sqsClient = runBlocking {
            SqsClient {
                this.region = localstack.region
                this.endpointUrl = Url.parse(endpoint)
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = localstack.accessKey
                    this.secretAccessKey = localstack.secretKey
                }
            }
        }

        get<ScheduledTaskRunner>().start(dataSource)

        get<br.com.soat.consumer.InboundEventConsumer>().start()

        server = KtorHttpServer(koin = koinApplication.koin, port = serverPort, wait = false)
        server.start()
    }

    private fun provisionMessaging(endpoint: String): Triple<String, String, String> = runBlocking {
        val sns = AwsSnsClient {
            this.region = localstack.region
            this.endpointUrl = Url.parse(endpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localstack.accessKey
                this.secretAccessKey = localstack.secretKey
            }
        }
        val sqs = SqsClient {
            this.region = localstack.region
            this.endpointUrl = Url.parse(endpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = localstack.accessKey
                this.secretAccessKey = localstack.secretKey
            }
        }

        val topicArn = sns.createTopic(CreateTopicRequest { name = "auto-repair-shop-billing-events-test" }).topicArn!!

        val subUrl = sqs.createQueue(CreateQueueRequest { queueName = "billing-events-subscriber-test" }).queueUrl!!
        val subArn = sqs.getQueueAttributes(GetQueueAttributesRequest {
            queueUrl = subUrl
            attributeNames = listOf(QueueAttributeName.QueueArn)
        }).attributes!![QueueAttributeName.QueueArn]!!
        sns.subscribe(SubscribeRequest {
            this.topicArn = topicArn
            protocol = "sqs"
            this.endpoint = subArn
            attributes = mapOf("RawMessageDelivery" to "true")
        })

        val sagaUrl = sqs.createQueue(CreateQueueRequest { queueName = "auto-repair-shop-billing-saga-test" }).queueUrl!!

        sns.close()
        sqs.close()
        Triple(topicArn, subUrl, sagaUrl)
    }

    @AfterAll
    fun tearDown() {
        server.stop()
        get<ScheduledTaskRunner>().stop()
        sqsClient.close()
        stopKoin()
    }

    inline fun <reified T> get(): T = koinApplication.koin.get()

    protected fun sendSagaMessage(body: String, eventType: String) {
        runBlocking {
            sqsClient.sendMessage(SendMessageRequest {
                queueUrl = sagaQueueUrl
                messageBody = body
                messageAttributes = mapOf(
                    "eventType" to MessageAttributeValue {
                        dataType = "String"
                        stringValue = eventType
                    },
                )
            })
        }
    }

    data class PublishedEvent(val envelope: JsonNode, val attributes: Map<String, String>) {
        val payload: JsonNode get() = envelope["payload"]
    }

    protected fun sagaQueueDepth(): Int = runBlocking {
        val attrs = sqsClient.getQueueAttributes(GetQueueAttributesRequest {
            queueUrl = sagaQueueUrl
            attributeNames = listOf(
                QueueAttributeName.ApproximateNumberOfMessages,
                QueueAttributeName.ApproximateNumberOfMessagesNotVisible,
            )
        }).attributes.orEmpty()
        (attrs[QueueAttributeName.ApproximateNumberOfMessages]?.toIntOrNull() ?: 0) +
            (attrs[QueueAttributeName.ApproximateNumberOfMessagesNotVisible]?.toIntOrNull() ?: 0)
    }

    protected fun await(timeoutSeconds: Long = 10, condition: () -> Boolean) {
        val deadline = Instant.now().plusSeconds(timeoutSeconds)
        while (Instant.now().isBefore(deadline)) {
            if (condition()) return
            Thread.sleep(200)
        }
        throw AssertionError("Condition not met within ${timeoutSeconds}s")
    }

    protected fun waitForPublishedEvent(eventType: String, timeoutSeconds: Long = 10): PublishedEvent {
        val mapper = get<ObjectMapper>()
        val deadline = Instant.now().plusSeconds(timeoutSeconds)
        while (Instant.now().isBefore(deadline)) {
            val msg = runBlocking {
                sqsClient.receiveMessage(ReceiveMessageRequest {
                    queueUrl = subscriberQueueUrl
                    maxNumberOfMessages = 1
                    waitTimeSeconds = 1
                    messageAttributeNames = listOf("All")
                }).messages?.firstOrNull()
            }
            if (msg != null) {
                val envelope = mapper.readTree(msg.body)
                val attributes = msg.messageAttributes.orEmpty()
                    .mapValues { it.value.stringValue ?: "" }
                runBlocking {
                    sqsClient.deleteMessage(DeleteMessageRequest {
                        queueUrl = subscriberQueueUrl
                        receiptHandle = msg.receiptHandle
                    })
                }
                if (envelope["eventType"]?.asText() == eventType) {
                    return PublishedEvent(envelope, attributes)
                }
            }
        }
        throw AssertionError("Timeout waiting for published event eventType=$eventType")
    }

    private fun testModule(config: Config) = module {
        single<Config> { config }
    }
}
