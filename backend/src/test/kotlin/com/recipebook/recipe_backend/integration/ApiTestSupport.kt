package com.recipebook.recipe_backend.integration

import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/** What the newer integration tests share: sending a GraphQL document and picking values out of the reply. */
class Api(private val mockMvc: MockMvc) {

    private fun send(query: String, token: String?): MockHttpServletResponse {
        val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        val request = post("/graphql").contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
            .content("""{"query":"$escaped"}""")
        if (token != null) request.header("Authorization", "Bearer $token")
        return mockMvc.perform(request).andReturn().response
    }

    /** The raw response body. Never asserts the HTTP status: a refused token answers 401 with no body. */
    fun graphql(query: String, token: String? = null): String = send(query, token).getContentAsString(Charsets.UTF_8)

    fun status(query: String, token: String?): Int = send(query, token).status

    /** The first `"id":"…"` in [response]. */
    fun id(response: String): String = response.substringAfter("\"id\":\"").substringBefore("\"")

    /** The `extensions.code` of the first error, or null when the response has none. */
    fun code(response: String): String? =
        if (response.contains("\"code\":\"")) response.substringAfter("\"code\":\"").substringBefore("\"") else null

    fun failed(response: String): Boolean = response.contains("\"errors\"")
}
