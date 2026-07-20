package br.com.soat.consumer

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sqs.SqsClient as AwsSqsClient
import aws.sdk.kotlin.services.sqs.model.DeleteMessageRequest
import aws.sdk.kotlin.services.sqs.model.ReceiveMessageRequest
import aws.smithy.kotlin.runtime.net.url.Url
import kotlinx.coroutines.runBlocking

class SqsClient(
    private val queueUrl: String,
    region: String = "us-east-1",
    endpointOverride: String? = null,
    accessKeyId: String? = null,
    secretAccessKey: String? = null,
) : MessageQueue, AutoCloseable {

    private val client: AwsSqsClient = runBlocking {
        AwsSqsClient {
            this.region = region
            if (endpointOverride != null) {
                this.endpointUrl = Url.parse(endpointOverride)
            }
            if (accessKeyId != null && secretAccessKey != null) {
                this.credentialsProvider = StaticCredentialsProvider {
                    this.accessKeyId = accessKeyId
                    this.secretAccessKey = secretAccessKey
                }
            }
        }
    }

    override fun receive(maxMessages: Int, waitSeconds: Int): List<Message> = runBlocking {
        client.receiveMessage(
            ReceiveMessageRequest {
                queueUrl = this@SqsClient.queueUrl
                maxNumberOfMessages = maxMessages
                waitTimeSeconds = waitSeconds
            },
        ).messages?.mapNotNull { msg ->
            val body = msg.body ?: return@mapNotNull null
            val handle = msg.receiptHandle ?: return@mapNotNull null
            Message(body, handle)
        } ?: emptyList()
    }

    override fun delete(receiptHandle: String) {
        runBlocking {
            client.deleteMessage(
                DeleteMessageRequest {
                    queueUrl = this@SqsClient.queueUrl
                    this.receiptHandle = receiptHandle
                },
            )
        }
    }

    override fun close() {
        client.close()
    }
}
