package com.recipebook.recipe_backend.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Duration
import kotlin.test.assertEquals

class RateLimitServiceTest {

    private val service = RateLimitService()

    private fun request(remote: String = "10.0.0.1", headers: Map<String, String> = emptyMap()) =
        MockHttpServletRequest().apply {
            remoteAddr = remote
            headers.forEach { (k, v) -> addHeader(k, v) }
        }

    @Test
    fun `uses the address Cloudflare saw`() {
        val r = request(headers = mapOf("CF-Connecting-IP" to "203.0.113.9"))
        assertEquals("203.0.113.9", service.clientIp(r))
    }

    @Test
    fun `ignores a caller-supplied X-Forwarded-For`() {
        // Cloudflare appends to this header rather than replacing it, so its first entry is
        // whatever the caller sent. Keying on it made every per-address limit bypassable.
        val r = request(remote = "10.0.0.7", headers = mapOf("X-Forwarded-For" to "198.51.100.1, 203.0.113.9"))
        assertEquals("10.0.0.7", service.clientIp(r))
    }

    @Test
    fun `forged X-Forwarded-For values all count against the same address`() {
        val limit = 20
        repeat(limit) { i ->
            val r = request(headers = mapOf("CF-Connecting-IP" to "203.0.113.9", "X-Forwarded-For" to "198.51.100.$i"))
            service.check("login-ip:${service.clientIp(r)}", limit, Duration.ofMinutes(15))
        }
        val forged = request(headers = mapOf("CF-Connecting-IP" to "203.0.113.9", "X-Forwarded-For" to "192.0.2.200"))
        assertThrows<RateLimitExceededException> {
            service.check("login-ip:${service.clientIp(forged)}", limit, Duration.ofMinutes(15))
        }
    }

    @Test
    fun `very long keys are capped so they share one window`() {
        val base = "login-user:" + "x".repeat(500)
        service.check(base + "a", 1, Duration.ofMinutes(15))
        assertThrows<RateLimitExceededException> {
            service.check(base + "b", 1, Duration.ofMinutes(15))
        }
    }

    @Test
    fun `failed logins from one address do not lock the owner out elsewhere`() {
        val window = Duration.ofMinutes(15)
        repeat(10) { service.check("login-user:198.51.100.66:family", 10, window) }
        assertThrows<RateLimitExceededException> { service.check("login-user:198.51.100.66:family", 10, window) }
        // The owner's own address has its own window.
        service.check("login-user:203.0.113.5:family", 10, window)
    }
}
