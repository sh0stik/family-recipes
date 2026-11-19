package recipe

import data.ApiException
import model.Ingredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NewIngredientFlowTest {
    private val request = NewIngredientRequest("Quince", 57.0, 0.4, 0.1, 15.3)
    private val created = Ingredient(id = "42", name = "Quince", caloriesPer100g = 57.0)

    @Test
    fun successReturnsTheIngredientAndLeavesNothingToShow() {
        val flow = NewIngredientFlow()
        flow.started()
        assertTrue(flow.busy)
        assertEquals(created, flow.finished(request, Result.success(created)))
        assertFalse(flow.busy)
        assertNull(flow.error)
        assertNull(flow.awaitingTerms)
    }

    @Test
    fun termsRequiredAsksForTheRulesThenSendsTheSameRequestAgain() {
        val flow = NewIngredientFlow()
        flow.started()
        assertNull(flow.finished(request, Result.failure(ApiException("TERMS_REQUIRED", "Accept the content rules"))))
        assertEquals(request, flow.awaitingTerms)
        assertNull(flow.error, "TERMS_REQUIRED opens the rules, it is not shown as a message")

        val retry = flow.termsAccepted()
        assertEquals(request, retry)
        assertNull(flow.awaitingTerms)

        flow.started()
        assertEquals(created, flow.finished(retry!!, Result.success(created), afterTerms = true))
        assertNull(flow.error)
    }

    @Test
    fun decliningTheRulesCreatesNothingAndShowsNothing() {
        val flow = NewIngredientFlow()
        flow.started()
        flow.finished(request, Result.failure(ApiException("TERMS_REQUIRED", "Accept the content rules")))
        flow.termsDeclined()
        assertNull(flow.awaitingTerms)
        assertNull(flow.error)
        assertFalse(flow.busy)
    }

    @Test
    fun termsRequiredAgainAfterAcceptingIsShownInsteadOfAskingInALoop() {
        val flow = NewIngredientFlow()
        val again = ApiException("TERMS_REQUIRED", "Accept the content rules")
        flow.started()
        flow.finished(request, Result.failure(again), afterTerms = true)
        assertNull(flow.awaitingTerms)
        assertSame(again, flow.error)
    }

    @Test
    fun blockedWordIsKeptForTheMessageWithItsWord() {
        val flow = NewIngredientFlow()
        flow.started()
        val e = ApiException("BLOCKED_WORD", "The word \"spam\" is not allowed in an ingredient name", word = "spam")
        assertNull(flow.finished(request, Result.failure(e)))
        assertSame(e, flow.error)
        assertEquals("spam", (flow.error as ApiException).word)
        assertNull(flow.awaitingTerms)
        assertFalse(flow.busy)
    }

    @Test
    fun anyOtherFailureIsKeptForTheMessageAndClearedOnTheNextTry() {
        val flow = NewIngredientFlow()
        val offline = IllegalStateException("no network")
        flow.started()
        flow.finished(request, Result.failure(offline))
        assertSame(offline, flow.error)

        flow.started()
        assertNull(flow.error)
    }

    @Test
    fun closingTheDialogClearsTheMessage() {
        val flow = NewIngredientFlow()
        flow.started()
        flow.finished(request, Result.failure(ApiException("TOO_LONG", "Name is too long")))
        flow.clearError()
        assertNull(flow.error)
    }
}
