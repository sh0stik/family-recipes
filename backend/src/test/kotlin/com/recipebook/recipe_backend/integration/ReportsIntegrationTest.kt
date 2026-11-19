package com.recipebook.recipe_backend.integration

import com.recipebook.recipe_backend.user.User
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reporting, end to end (Apple Guideline 1.2: "a mechanism to report offensive content and timely
 * responses"; Google UGC policy: in-app reporting of content and users). A report is stored,
 * emailed to the moderation address, and takes the recipe out of the reporter's own lists.
 */
class ReportsIntegrationTest : ModerationTestBase() {

    private lateinit var author: User
    private lateinit var reporter: User
    private lateinit var bystander: User
    private lateinit var admin: User
    private lateinit var authorToken: String
    private lateinit var reporterToken: String
    private lateinit var bystanderToken: String
    private lateinit var adminToken: String
    private lateinit var publicRecipe: String
    private lateinit var privateRecipe: String

    @BeforeAll
    fun setUp() {
        author = user("reports-author"); reporter = user("reports-reporter"); bystander = user("reports-bystander")
        admin = user("moderation-admin")
        authorToken = token(author); reporterToken = token(reporter); bystanderToken = token(bystander); adminToken = token(admin)
        publicRecipe = createRecipe("Reports spam pie", authorToken, "PUBLIC")
        privateRecipe = createRecipe("Reports private pie", authorToken)
    }

    private fun reportRows(where: String = "true"): Int =
        jdbc.queryForObject("SELECT count(*) FROM content_reports WHERE $where", Int::class.java)!!

    private fun sentMails(): List<SimpleMailMessage> {
        val mails = argumentCaptor<SimpleMailMessage>()
        verify(mailSender, timeout(3000).atLeastOnce()).send(mails.capture())
        return mails.allValues
    }

    @Test
    fun `a reported recipe is stored, emailed, and gone from the reporter's lists only`() {
        val response = api.graphql("""mutation { reportRecipe(recipeId: "$publicRecipe", reason: SPAM, comment: "an advert for knives") }""", reporterToken)
        assertTrue(response.contains("\"reportRecipe\":true"), response)

        assertEquals(1, reportRows("recipe_id = '$publicRecipe' AND reporter_id = '${reporter.id}' AND reason = 'SPAM' AND reported_user_id = '${author.id}'"))

        val search = """searchRecipes(keyword: "Reports spam pie", ingredientName: "Reports spam pie")"""
        for (list in listOf("publicRecipes", "recipes", search)) {
            assertFalse(listed(list, publicRecipe, reporterToken), "$list still shows it to the reporter")
            assertTrue(listed(list, publicRecipe, bystanderToken), "$list lost it for someone else")
        }
        assertTrue(listed("publicRecipes", publicRecipe, null), "guests still see it")
        assertTrue(listed(search, publicRecipe, null))
        assertTrue(readable(publicRecipe, reporterToken), "a link to it still opens")
        assertTrue(listed("myRecipes", publicRecipe, authorToken))

        val mail = sentMails().first { it.text!!.contains(publicRecipe) }
        assertEquals(listOf("reports@test.local"), mail.to!!.toList())
        assertTrue(mail.subject!!.contains("Reports spam pie"), mail.subject)
        for (expected in listOf("Owner: reports-author", "Reported by: reports-reporter", "Reason: SPAM", "an advert for knives")) {
            assertTrue(mail.text!!.contains(expected), "$expected missing from:\n${mail.text}")
        }

        // Again: nothing new.
        api.graphql("""mutation { reportRecipe(recipeId: "$publicRecipe", reason: OTHER) }""", reporterToken)
        assertEquals(1, reportRows("recipe_id = '$publicRecipe' AND reporter_id = '${reporter.id}'"))
    }

    @Test
    fun `what cannot be reported`() {
        val own = api.graphql("""mutation { reportRecipe(recipeId: "$publicRecipe", reason: OTHER) }""", authorToken)
        assertEquals("CANNOT_REPORT", api.code(own), own)
        val self = api.graphql("""mutation { reportUser(userId: "${author.id}", reason: OTHER) }""", authorToken)
        assertEquals("CANNOT_REPORT", api.code(self), self)

        // A recipe the caller cannot read, and a guest.
        assertTrue(api.failed(api.graphql("""mutation { reportRecipe(recipeId: "$privateRecipe", reason: OTHER) }""", bystanderToken)))
        assertTrue(api.failed(api.graphql("""mutation { reportRecipe(recipeId: "$publicRecipe", reason: OTHER) }""")))
        assertEquals(0, reportRows("recipe_id = '$privateRecipe'"))
        assertEquals(0, reportRows("reporter_id = '${author.id}'"))

        val long = api.graphql("""mutation { reportRecipe(recipeId: "$publicRecipe", reason: OTHER, comment: "${"x".repeat(1001)}") }""", bystanderToken)
        assertEquals("TOO_LONG", api.code(long), long)
    }

    @Test
    fun `a reported user is stored and emailed`() {
        val response = api.graphql("""mutation { reportUser(userId: "${author.id}", reason: INAPPROPRIATE, comment: "rude in notes") }""", bystanderToken)
        assertTrue(response.contains("\"reportUser\":true"), response)
        assertEquals(1, reportRows("recipe_id IS NULL AND reported_user_id = '${author.id}' AND reporter_id = '${bystander.id}'"))
        assertTrue(sentMails().any { it.subject!!.contains("User reported: reports-author") && it.text!!.contains("rude in notes") })
    }

    @Test
    fun `a mail server that is down does not fail the report`() {
        val unlucky = user("reports-unlucky")
        doThrow(MailSendException("connection refused")).whenever(mailSender).send(any<SimpleMailMessage>())
        try {
            val response = api.graphql("""mutation { reportUser(userId: "${author.id}", reason: OTHER) }""", token(unlucky))
            assertTrue(response.contains("\"reportUser\":true"), response)
            assertEquals(1, reportRows("reporter_id = '${unlucky.id}'"))
        } finally {
            org.mockito.Mockito.reset(mailSender)
        }
    }

    @Test
    fun `one account cannot flood the moderation inbox`() {
        val flooder = token(user("reports-flooder"))
        val mutation = """mutation { reportRecipe(recipeId: "$publicRecipe", reason: SPAM) }"""
        repeat(10) { assertFalse(api.failed(api.graphql(mutation, flooder)), "report ${it + 1} of 10 was refused") }
        assertEquals("RATE_LIMITED", api.code(api.graphql(mutation, flooder)))
    }

    @Test
    fun `admins read the reports, nobody else does`() {
        // A reporter of their own: reporting hides the recipe from the reporter, which would
        // disturb the other tests' bystander.
        val blogger = token(user("reports-blogger"))
        api.graphql("""mutation { reportRecipe(recipeId: "$publicRecipe", reason: NOT_THEIRS, comment: "copied from my blog") }""", blogger)

        val query = "{ reports { id reason comment createdAt reporterUsername recipeId recipeName reportedUserId reportedUsername } }"
        val asAdmin = api.graphql(query, adminToken)
        assertFalse(api.failed(asAdmin), asAdmin)
        assertTrue(asAdmin.contains("copied from my blog") && asAdmin.contains("\"reason\":\"NOT_THEIRS\""), asAdmin)
        assertTrue(asAdmin.contains("\"reporterUsername\":\"reports-blogger\""), asAdmin)
        assertTrue(asAdmin.contains("\"recipeName\":\"Reports spam pie\"") && asAdmin.contains("\"reportedUsername\":\"reports-author\""), asAdmin)

        assertEquals("ADMIN_ONLY", api.code(api.graphql(query, bystanderToken)))
        assertTrue(api.failed(api.graphql(query)))
    }
}
