package errors

import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCodesTest {
    // Every code in VISIBILITY_API_NOTES.md ("Error codes"). A server code that is missing from
    // errorText shows the user a generic error, so a new code has to be added in both places.
    private val serverCodes = setOf(
        "TERMS_REQUIRED", "BLOCKED_WORD", "REMOVED_BY_ADMIN", "OWNER_ONLY", "BLOCKED", "CANNOT_REPORT",
        "CANNOT_BLOCK", "ADMIN_ONLY", "ACCOUNT_SUSPENDED", "USER_SUSPENDED", "USERNAME_TAKEN",
        "WRONG_PASSWORD", "RATE_LIMITED", "TOO_LONG", "EMPTY_FIELD", "INVALID_USERNAME", "INVALID_EMAIL",
        "INVALID_URL", "INVALID_LOCALE", "OUT_OF_RANGE", "INVALID_CREDENTIALS", "INVALID_INVITE_CODE",
        "EMAIL_TAKEN", "WEAK_PASSWORD", "ADMIN_CANNOT_DELETE",
        // Photo upload (POST /api/photos) and the one-photo rule on addPhotoToRecipe.
        "NOT_AN_IMAGE", "UNSUPPORTED_FORMAT", "TOO_LARGE", "UNAUTHENTICATED", "PHOTO_LIMIT", "PHOTO_NOT_YOURS",
    )

    @Test
    fun everyServerCodeHasAMessage() {
        assertEquals(emptySet(), serverCodes - knownErrorCodes)
    }
}
