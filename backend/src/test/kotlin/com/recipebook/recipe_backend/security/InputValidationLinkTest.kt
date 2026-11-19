package com.recipebook.recipe_backend.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class InputValidationLinkTest {

    @Test
    fun `http and https links are accepted`() {
        assertEquals("https://youtube.com/watch?v=abc", InputValidation.webLink("https://youtube.com/watch?v=abc", "Video link"))
        assertEquals("http://example.com/v", InputValidation.webLink("  http://example.com/v ", "Video link"))
        assertEquals("HTTPS://EXAMPLE.COM/A", InputValidation.webLink("HTTPS://EXAMPLE.COM/A", "Video link"))
    }

    @Test
    fun `script, data, file and hostless links are refused`() {
        // The audit (S-03) stored each of these; the web app opened video links with window.open.
        for (bad in listOf(
            "javascript:alert(document.domain)",
            "JavaScript:alert(1)",
            "data:text/html,<script>1</script>",
            "file:///etc/passwd",
            "ftp://example.com/x",
            "https://",
            "not a link",
            "https://example.com/" + "a".repeat(InputValidation.MAX_URL),
        )) {
            assertThrows<InvalidInputException>(bad) { InputValidation.webLink(bad, "Video link") }
        }
    }

    @Test
    fun `photos may also be files served from uploads`() {
        assertEquals("/uploads/abc.jpg", InputValidation.photoLink("/uploads/abc.jpg"))
        assertEquals("https://example.com/p.jpg", InputValidation.photoLink("https://example.com/p.jpg"))
        for (bad in listOf("/uploads/../application.yaml", "/uploads/a b.jpg", "/etc/passwd", "uploads/x.jpg", "javascript:alert(1)",
            // One file, one URL: no folders, queries or fragments that would make a second name for it.
            "/uploads/a/b.jpg", "/uploads/x.jpg?v=1", "/uploads/x.jpg#top", "/uploads/", "/uploads/.hidden")) {
            assertThrows<InvalidInputException>(bad) { InputValidation.photoLink(bad) }
        }
    }
}
