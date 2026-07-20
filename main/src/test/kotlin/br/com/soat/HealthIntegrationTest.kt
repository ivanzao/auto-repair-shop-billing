package br.com.soat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthIntegrationTest : IntegrationTest() {

    @Test
    fun `health endpoint returns 200`() {
        val response = http.get("/health")
        assertEquals(200, response.statusCode())
    }
}
