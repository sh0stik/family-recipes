package data

import com.apollographql.apollo3.exception.ApolloCompositeException
import com.apollographql.apollo3.exception.ApolloHttpException
import com.apollographql.apollo3.exception.ApolloNetworkException
import com.apollographql.apollo3.exception.ApolloParseException
import com.apollographql.apollo3.exception.CacheMissException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRejectionTest {
    private fun http(status: Int) =
        ApolloHttpException(status, emptyList(), null, "Http request failed with status code `$status`")

    private val cacheMiss = CacheMissException("QUERY_ROOT", "me")

    @Test
    fun a401IsARejection() {
        assertTrue(http(401).isSessionRejected())
    }

    @Test
    fun otherHttpStatusesAreNot() {
        assertFalse(http(403).isSessionRejected())
        assertFalse(http(500).isSessionRejected())
        assertFalse(http(502).isSessionRejected())
    }

    @Test
    fun beingOfflineIsNot() {
        assertFalse(ApolloNetworkException("Failed to execute GraphQL http network request").isSessionRejected())
        assertFalse(ApolloCompositeException(ApolloNetworkException("timeout"), cacheMiss).isSessionRejected())
    }

    @Test
    fun a401InsideACompositeIsARejection() {
        // What a NetworkFirst query with nothing cached throws: network failure first, cache miss
        // second, message "multiple exceptions happened", cause = the cache miss.
        val composite = ApolloCompositeException(http(401), cacheMiss)
        assertEquals("multiple exceptions happened", composite.message)
        assertTrue(composite.isSessionRejected())
        // CacheFirst puts them the other way round.
        assertTrue(ApolloCompositeException(cacheMiss, http(401)).isSessionRejected())
    }

    @Test
    fun a401WrappedAsACauseIsARejection() {
        assertTrue(Exception("Request failed", http(401)).isSessionRejected())
        assertTrue(Exception("outer", Exception("inner", http(401))).isSessionRejected())
    }

    @Test
    fun a401AddedAsSuppressedIsARejection() {
        val e = Exception("several things failed")
        e.addSuppressed(http(401))
        assertTrue(e.isSessionRejected())
    }

    @Test
    fun theGraphqlUnauthorizedErrorStillCounts() {
        // An expired token, or one issued under a name since changed, is treated as anonymous:
        // the server answers 200 with this GraphQL error rather than 401.
        assertTrue(ApiException(null, "Unauthorized").isSessionRejected())
        assertTrue(ApiException(null, "Access Denied").isSessionRejected())
    }

    @Test
    fun otherServerRefusalsAreNot() {
        assertFalse(ApiException("WRONG_PASSWORD", "Current password is incorrect").isSessionRejected())
        assertFalse(ApiException("RATE_LIMITED", "Too many attempts").isSessionRejected())
        // The old check matched "401" anywhere in the message, and these ids are random.
        assertFalse(ApiException(null, "INTERNAL_ERROR for 4015a2c3-9b1e-4d1f-8a40-401b2c3d4e5f").isSessionRejected())
        assertFalse(ApolloParseException("Failed to parse GraphQL http network response").isSessionRejected())
    }

    @Test
    fun aCauseCycleDoesNotHang() {
        val a = Exception("a")
        val b = Exception("b", a)
        a.addSuppressed(b)
        assertFalse(a.isSessionRejected())
    }

    @Test
    fun theMonitorClearsTheTokenThatWasRejectedAndSaysSo() {
        var stored: String? = "old"
        val monitor = SessionMonitor({ stored }, { stored = null })
        monitor.rejected("old")
        assertNull(stored)
        assertEquals(1, monitor.rejections.value)
    }

    @Test
    fun theMonitorIgnoresATokenThatIsNoLongerStored() {
        // Signed out, or signed in again, while the request was on its way.
        var stored: String? = "new"
        val monitor = SessionMonitor({ stored }, { stored = null })
        monitor.rejected("old")
        assertEquals("new", stored)
        assertEquals(0, monitor.rejections.value)

        stored = null
        monitor.rejected("old")
        assertEquals(0, monitor.rejections.value)
    }
}
