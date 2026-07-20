package br.com.soat

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class IntegrationTestHttpClient(private val serverPort: Int) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun get(path: String, headers: Map<String, String> = emptyMap()): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
    }

    fun post(
        path: String,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$serverPort$path"))
            .header("Content-Type", "application/json")
        headers.forEach { (k, v) -> builder.header(k, v) }
        return client.send(
            builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }
}
