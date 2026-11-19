package errors

import com.apollographql.apollo3.exception.ApolloNetworkException
import data.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorMessagesTest {
    private fun keyAndArgs(m: ErrorMessage) = m.text.key to m.args

    @Test
    fun blockedWordInAnIngredientNamesTheWordAndSaysIngredient() {
        val e = ApiException("BLOCKED_WORD", "The word \"spam\" is not allowed in an ingredient name", word = "spam")
        assertEquals(
            "error_blocked_word_ingredient" to listOf("spam"),
            keyAndArgs(errorMessage(e, blockedWordIn = BlockedWordIn.INGREDIENT)),
        )
    }

    @Test
    fun blockedWordInARecipeKeepsTheRecipeMessage() {
        val e = ApiException("BLOCKED_WORD", "blocked", word = "spam")
        assertEquals("error_blocked_word" to listOf("spam"), keyAndArgs(errorMessage(e)))
    }

    @Test
    fun aKnownCodeGetsItsOwnMessageWhereverItHappens() {
        val e = ApiException("TOO_LONG", "Name is too long")
        assertEquals("error_too_long" to emptyList(), keyAndArgs(errorMessage(e, blockedWordIn = BlockedWordIn.INGREDIENT)))
    }

    @Test
    fun noConnectionAndUnknownFailuresGetTheUsualMessages() {
        assertEquals("error_network", errorMessage(ApolloNetworkException("offline")).text.key)
        assertEquals("error_generic", errorMessage(ApiException(null, "Failed")).text.key)
        assertEquals("error_generic", errorMessage(ApiException("SOMETHING_NEW", "?")).text.key)
        assertEquals("error_generic", errorMessage(IllegalStateException("boom")).text.key)
    }
}
