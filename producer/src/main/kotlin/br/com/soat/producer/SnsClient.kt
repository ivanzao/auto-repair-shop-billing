package br.com.soat.producer

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sns.model.MessageAttributeValue
import aws.sdk.kotlin.services.sns.model.PublishRequest
import aws.smithy.kotlin.runtime.net.url.Url
import aws.sdk.kotlin.services.sns.SnsClient as AwsSnsClient
import kotlinx.coroutines.runBlocking

class SnsClient(
    private val topicArn: String,
    region: String = "us-east-1",
    endpointOverride: String? = null,
    accessKeyId: String? = null,
    secretAccessKey: String? = null,
) : SnsPort, AutoCloseable {

    private val client: AwsSnsClient = runBlocking {
        AwsSnsClient {
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

    override fun publish(payload: String, eventType: String, messageId: String, traceparent: String?) {
        runBlocking {
            client.publish(
                PublishRequest {
                    topicArn = this@SnsClient.topicArn
                    message = payload
                    messageAttributes = buildMap {
                        put("eventType", MessageAttributeValue {
                            dataType = "String"
                            stringValue = eventType
                        })
                        put("messageId", MessageAttributeValue {
                            dataType = "String"
                            stringValue = messageId
                        })
                        if (traceparent != null) {
                            put("traceparent", MessageAttributeValue {
                                dataType = "String"
                                stringValue = traceparent
                            })
                        }
                    }
                },
            )
        }
    }

    override fun close() {
        client.close()
    }
}
