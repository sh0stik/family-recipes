package recipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import data.apiCode
import model.Ingredient

/** An ingredient the user asked to create: the name typed in the picker and its nutrition. */
data class NewIngredientRequest(
    val name: String,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val fatPer100g: Double,
    val carbsPer100g: Double,
)

/**
 * What happens around createIngredient, shared by the create and edit screens. The server refuses
 * a new ingredient with TERMS_REQUIRED until the content rules are accepted, and with BLOCKED_WORD
 * for a blocked word in the name; any other call can fail too. A failure used to be dropped, so the
 * ingredient just never appeared.
 *
 * The nutrition dialog stays open while the request runs ([busy]) and shows [error] when it fails,
 * with what was typed still there, as the content rules dialog does. TERMS_REQUIRED puts the
 * request in [awaitingTerms] instead: the screen shows the content rules, and [termsAccepted] hands
 * the request back to be sent again. The screen makes the calls; this only decides what follows.
 */
class NewIngredientFlow {
    /** A request refused with TERMS_REQUIRED, waiting for the user to accept the content rules. */
    var awaitingTerms by mutableStateOf<NewIngredientRequest?>(null)
        private set

    /** Why the last request failed, for the nutrition dialog to show. Null when there is nothing to show. */
    var error by mutableStateOf<Throwable?>(null)
        private set

    /** A request is on its way. */
    var busy by mutableStateOf(false)
        private set

    /** A request is about to be sent: the previous message no longer applies. */
    fun started() {
        busy = true
        error = null
    }

    /**
     * The server's answer to [request]. Returns the new ingredient on success. [afterTerms] is true
     * for the retry that follows accepting the rules: a second TERMS_REQUIRED then is shown as a
     * message instead of asking again, so the rules dialog cannot come back in a loop.
     */
    fun finished(request: NewIngredientRequest, result: Result<Ingredient>, afterTerms: Boolean = false): Ingredient? {
        busy = false
        val e = result.exceptionOrNull() ?: return result.getOrNull()
        if (e.apiCode == "TERMS_REQUIRED" && !afterTerms) awaitingTerms = request else error = e
        return null
    }

    /** The rules were accepted: the request to send again. */
    fun termsAccepted(): NewIngredientRequest? = awaitingTerms.also { awaitingTerms = null }

    /** The rules were declined: nothing is created, and nothing more is said. */
    fun termsDeclined() {
        awaitingTerms = null
    }

    /** The nutrition dialog was closed: its message goes with it. */
    fun clearError() {
        error = null
    }
}
