package data

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoUserAgentTest {
    @Test
    fun namesTheAppItsVersionPlatformAndContact() {
        assertEquals(
            "MomsRecipes/1.2.3 (Android; com.daria.recipes; +https://recipes.example.com/support.html)",
            PhotoUserAgent.of("1.2.3", "Android"),
        )
        assertEquals(
            "MomsRecipes/1.0 (iOS; com.daria.recipes; +https://recipes.example.com/support.html)",
            PhotoUserAgent.of("1.0", "iOS"),
        )
    }

    @Test
    fun aMissingOrOddVersionFallsBack() {
        val expected = "MomsRecipes/${PhotoUserAgent.FALLBACK_VERSION} (Desktop; com.daria.recipes; +https://recipes.example.com/support.html)"
        assertEquals(expected, PhotoUserAgent.of(null, "Desktop"))
        assertEquals(expected, PhotoUserAgent.of("  ", "Desktop"))
        assertEquals(expected, PhotoUserAgent.of("1.0 beta", "Desktop"))
    }
}
