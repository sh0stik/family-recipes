package com.recipebook.recipe_backend.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Covers the browser rejecting the Kotlin/JS web client's first `/graphql` call:
 * "No 'Access-Control-Allow-Origin' header is present on the requested resource." Spring Security
 * does not add CORS headers unless a `CorsConfigurationSource` is wired into `HttpSecurity.cors()` —
 * without it, the preflight `OPTIONS` request Chrome sends before every cross-origin POST gets no
 * `Access-Control-Allow-Origin` header at all, so the browser blocks the real request before it is
 * even sent, regardless of what `/graphql` itself would have returned.
 *
 * Uses its own `recipe.cors.allowed-origins` so the allowed origin is explicit here rather than
 * relying on the production default, and so this class does not depend on what other test classes
 * configure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = ["recipe.cors.allowed-origins=http://localhost:8085"])
class CorsIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val allowedOrigin = "http://localhost:8085"
    private val disallowedOrigin = "https://evil.example.com"

    @Test
    fun `preflight from an allowed origin gets Access-Control-Allow-Origin`() {
        mockMvc.perform(
            options("/graphql")
                .header(HttpHeaders.ORIGIN, allowedOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, Authorization")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, org.hamcrest.Matchers.containsString("POST")))
    }

    @Test
    fun `preflight from a disallowed origin gets no Access-Control-Allow-Origin`() {
        mockMvc.perform(
            options("/graphql")
                .header(HttpHeaders.ORIGIN, disallowedOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
        )
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `a normal request with no Origin header still reaches graphql`() {
        // Non-browser clients (curl, Android, iOS) never send an Origin header. CORS must not
        // require one — this is what proves the security chain still treats /graphql as permitAll
        // for a plain POST, exactly as before this change.
        mockMvc.perform(
            post("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"query":"{ __typename }"}""")
        )
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }
}
