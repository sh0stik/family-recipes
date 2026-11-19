package com.recipebook.recipe_backend.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class InputValidationTest {

    @Test
    fun `usernames are trimmed and must be letters, digits, dot, underscore or hyphen`() {
        assertEquals("Marina", InputValidation.username("  Marina "))
        assertEquals("Мария_2", InputValidation.username("Мария_2"))
        for (bad in listOf("", "   ", "ab", "family family", "<script>alert(1)</script>", "x".repeat(31), "a/b", "anonymousUser", "AnonymousUser")) {
            assertThrows<InvalidInputException>(bad) { InputValidation.username(bad) }
        }
    }

    @Test
    fun `emails need one at sign and a dotted domain`() {
        assertEquals("a@b.co", InputValidation.email(" a@b.co "))
        for (bad in listOf("notanemail", "a@b", "a b@c.de", "@b.co", "a@@b.co")) {
            assertThrows<InvalidInputException>(bad) { InputValidation.email(bad) }
        }
    }

    @Test
    fun `nutrition values must be between 0 and 1000 per 100 g`() {
        assertEquals(0f, InputValidation.per100g(0f, "Fat"))
        assertEquals(1000f, InputValidation.per100g(1000f, "Fat"))
        for (bad in listOf(-5f, 1000.5f, 1e30f, Float.NaN)) {
            assertThrows<InvalidInputException>(bad.toString()) { InputValidation.per100g(bad, "Fat") }
        }
    }

    @Test
    fun `only the app's languages are accepted`() {
        assertEquals("ru", InputValidation.locale("ru"))
        assertThrows<InvalidInputException> { InputValidation.locale("zz".repeat(500)) }
    }

    @Test
    fun `text limits refuse empty required fields and oversized ones`() {
        assertThrows<InvalidInputException> { InputValidation.text("  ", 10, "Name", required = true) }
        assertThrows<InvalidInputException> { InputValidation.text("x".repeat(11), 10, "Name") }
        assertEquals("ok", InputValidation.text("ok", 10, "Name"))
    }
}
