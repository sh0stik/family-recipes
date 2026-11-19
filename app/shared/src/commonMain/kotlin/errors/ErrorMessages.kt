package errors

import androidx.compose.runtime.Composable
import com.apollographql.apollo3.exception.ApolloNetworkException
import data.ApiException
import data.UploadNetworkException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import recipeappkmp.generated.resources.*

/** One message per server code (VISIBILITY_API_NOTES.md, "Error codes"). */
private val codeMessages: Map<String, StringResource> = mapOf(
    "TERMS_REQUIRED" to Res.string.error_terms_required,
    "REMOVED_BY_ADMIN" to Res.string.error_removed_by_admin,
    "OWNER_ONLY" to Res.string.error_owner_only,
    "BLOCKED" to Res.string.error_blocked,
    "CANNOT_REPORT" to Res.string.error_cannot_report,
    "CANNOT_BLOCK" to Res.string.error_cannot_block,
    "ADMIN_ONLY" to Res.string.error_admin_only,
    "ACCOUNT_SUSPENDED" to Res.string.error_account_suspended,
    "USER_SUSPENDED" to Res.string.error_user_suspended,
    "USERNAME_TAKEN" to Res.string.error_username_taken,
    "WRONG_PASSWORD" to Res.string.error_wrong_password,
    "RATE_LIMITED" to Res.string.error_rate_limited,
    "TOO_LONG" to Res.string.error_too_long,
    "EMPTY_FIELD" to Res.string.error_empty_field,
    "INVALID_USERNAME" to Res.string.error_invalid_username,
    "INVALID_EMAIL" to Res.string.error_invalid_email,
    "INVALID_URL" to Res.string.error_invalid_url,
    "INVALID_LOCALE" to Res.string.error_invalid_locale,
    "OUT_OF_RANGE" to Res.string.error_out_of_range,
    "INVALID_CREDENTIALS" to Res.string.error_invalid_credentials,
    "INVALID_INVITE_CODE" to Res.string.error_invalid_invite_code,
    "EMAIL_TAKEN" to Res.string.error_email_taken,
    "WEAK_PASSWORD" to Res.string.error_weak_password,
    "ADMIN_CANNOT_DELETE" to Res.string.error_admin_cannot_delete,
    // Photos: the upload endpoint (POST /api/photos) and addPhotoToRecipe.
    "NOT_AN_IMAGE" to Res.string.error_not_an_image,
    "UNSUPPORTED_FORMAT" to Res.string.error_unsupported_format,
    "TOO_LARGE" to Res.string.error_too_large,
    "PHOTO_LIMIT" to Res.string.error_photo_limit,
    "PHOTO_NOT_YOURS" to Res.string.error_photo_not_yours,
    // An upload answered 401: the app is being signed out (SessionMonitor) as this shows.
    "UNAUTHENTICATED" to Res.string.session_ended,
)

/** The codes [errorText] has a message for. Checked by the tests against the API notes. */
val knownErrorCodes: Set<String> get() = codeMessages.keys + "BLOCKED_WORD"

/** What a BLOCKED_WORD refusal was about: the same code needs different words for each. */
enum class BlockedWordIn { RECIPE, INGREDIENT }

/** A message for the user, before it is put into words: the string and its arguments. */
data class ErrorMessage(val text: StringResource, val args: List<String> = emptyList())

/** The message [errorText] shows, picked without Compose so the tests can check it. */
fun errorMessage(
    error: Throwable,
    removalReason: String? = null,
    blockedWordIn: BlockedWordIn = BlockedWordIn.RECIPE,
): ErrorMessage {
    val api = error as? ApiException
    return when {
        api?.code == "BLOCKED_WORD" -> ErrorMessage(
            when (blockedWordIn) {
                BlockedWordIn.RECIPE -> Res.string.error_blocked_word
                BlockedWordIn.INGREDIENT -> Res.string.error_blocked_word_ingredient
            },
            listOf(api.word ?: ""),
        )
        api?.code == "REMOVED_BY_ADMIN" && !removalReason.isNullOrBlank() ->
            ErrorMessage(Res.string.error_removed_by_admin_reason, listOf(removalReason))
        api?.code != null && api.code in codeMessages -> ErrorMessage(codeMessages.getValue(api.code))
        error is ApolloNetworkException || error is UploadNetworkException -> ErrorMessage(Res.string.error_network)
        else -> ErrorMessage(Res.string.error_generic)
    }
}

/**
 * What to tell the user about a failure, in their language. [removalReason] is the admin's
 * reason for REMOVED_BY_ADMIN: the server's error does not carry it, but a screen that loaded the
 * recipe has it. [blockedWordIn] says whether a blocked word was in a recipe or an ingredient name.
 */
@Composable
fun errorText(
    error: Throwable,
    removalReason: String? = null,
    blockedWordIn: BlockedWordIn = BlockedWordIn.RECIPE,
): String {
    val message = errorMessage(error, removalReason, blockedWordIn)
    return if (message.args.isEmpty()) stringResource(message.text)
    else stringResource(message.text, *message.args.toTypedArray())
}
