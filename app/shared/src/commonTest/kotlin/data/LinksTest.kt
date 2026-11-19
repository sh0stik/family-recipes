package data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinksTest {
    private val base = "https://api.example.com"

    @Test
    fun webLinksAreAccepted() {
        assertTrue(Links.isWebLink("https://youtu.be/abc"))
        assertTrue(Links.isWebLink("http://example.com/v"))
        assertTrue(Links.isWebLink("  HTTPS://Example.com/x  "))
    }

    @Test
    fun otherSchemesAndJunkAreRejected() {
        listOf(
            "javascript:alert(document.domain)",
            "JavaScript:alert(1)",
            "data:text/html,<script>1</script>",
            "file:///etc/passwd",
            "intent://x#Intent;end",
            "https://",
            "https://a b",
            "example.com",
            "",
            null,
        ).forEach { assertFalse(Links.isWebLink(it), "accepted: $it") }
    }

    @Test
    fun imagesKeepWebLinksAndServerPaths() {
        assertEquals("https://e.x/p.jpg", Links.imageUrl("https://e.x/p.jpg", base))
        assertEquals("http://e.x/p.jpg", Links.imageUrl("http://e.x/p.jpg", base))
        assertEquals("$base/uploads/abc.jpg", Links.imageUrl("/uploads/abc.jpg", base))
        assertEquals("$base/uploads/abc.jpg", Links.imageUrl("uploads/abc.jpg", base))
    }

    @Test
    fun imagesWithOtherSchemesAreDropped() {
        assertNull(Links.imageUrl("javascript:alert(1)", base))
        assertNull(Links.imageUrl("data:image/png;base64,AAAA", base))
        assertNull(Links.imageUrl("file:///etc/passwd", base))
        assertNull(Links.imageUrl("//evil.example/x.jpg", base))
        assertNull(Links.imageUrl("", base))
        assertNull(Links.imageUrl(null, base))
    }
}
