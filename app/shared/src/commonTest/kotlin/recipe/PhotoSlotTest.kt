package recipe

import data.ApiException
import data.apiCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoSlotTest {
    private val jpeg = byteArrayOf(1, 2, 3)

    @Test
    fun aLinkFillsTheEmptySlot() {
        val slot = PhotoSlot().withLink("  https://example.com/pie.jpg ")!!
        assertEquals("https://example.com/pie.jpg", slot.photo)
        assertEquals(listOf("https://example.com/pie.jpg"), slot.photos)
    }

    @Test
    fun somethingThatIsNotAWebLinkIsNotTaken() {
        assertNull(PhotoSlot().withLink("javascript:alert(1)"))
        assertNull(PhotoSlot().withLink("pie.jpg"))
    }

    @Test
    fun anUploadFillsTheSlotOnceTheServerAnswers() {
        val started = PhotoSlot().uploadStarted(jpeg)
        assertTrue(started.isUploading)
        assertNull(started.photo)
        val done = started.uploadFinished(Result.success("/uploads/abc.jpg"))
        assertEquals(listOf("/uploads/abc.jpg"), done.photos)
        assertTrue(!done.isUploading)
        assertNull(done.error)
    }

    @Test
    fun replacingByUploadOrByLinkLeavesOnePhoto() {
        val linked = PhotoSlot(listOf("https://example.com/old.jpg"))
        val uploaded = linked.uploadStarted(jpeg).uploadFinished(Result.success("/uploads/new.jpg"))
        assertEquals(listOf("/uploads/new.jpg"), uploaded.photos)
        val backToLink = uploaded.withLink("https://example.com/other.jpg")!!
        assertEquals(listOf("https://example.com/other.jpg"), backToLink.photos)
    }

    @Test
    fun aFailedUploadKeepsThePhotoThatWasThere() {
        val slot = PhotoSlot(listOf("https://example.com/old.jpg"))
            .uploadStarted(jpeg)
            .uploadFinished(Result.failure(ApiException("TOO_LARGE", "too large")))
        assertEquals(listOf("https://example.com/old.jpg"), slot.photos)
        assertEquals("TOO_LARGE", slot.error?.apiCode)
        assertTrue(!slot.isUploading)
    }

    @Test
    fun anUnreadablePickSaysNotAnImage() {
        assertEquals("NOT_AN_IMAGE", PhotoSlot().unreadable().error?.apiCode)
    }

    @Test
    fun aRecipeFromBeforeTheRuleShowsTheFirstAndCanBeTrimmed() {
        val legacy = PhotoSlot(listOf("https://a.example/1.jpg", "https://a.example/2.jpg", "/uploads/3.jpg"))
        assertEquals("https://a.example/1.jpg", legacy.photo)
        assertEquals(listOf("https://a.example/2.jpg", "/uploads/3.jpg"), legacy.extras)
        val trimmed = legacy.without("https://a.example/2.jpg").without("/uploads/3.jpg")
        assertEquals(listOf("https://a.example/1.jpg"), trimmed.photos)
        assertTrue(trimmed.extras.isEmpty())
        // Replacing drops the extras too.
        assertEquals(listOf("https://b.example/x.jpg"), legacy.withLink("https://b.example/x.jpg")!!.photos)
    }

    @Test
    fun removingEmptiesTheSlot() {
        assertNull(PhotoSlot(listOf("/uploads/a.jpg")).without("/uploads/a.jpg").photo)
    }
}
