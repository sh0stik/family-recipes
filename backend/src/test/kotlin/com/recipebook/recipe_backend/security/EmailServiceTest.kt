package com.recipebook.recipe_backend.security

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class EmailServiceTest {

    private val mailSender: JavaMailSender = mock()

    private fun sentMessage(locale: String, linkBaseUrl: String = ""): SimpleMailMessage {
        val emailService = EmailService(mailSender, "from@test.com", linkBaseUrl)
        val captor = argumentCaptor<SimpleMailMessage>()

        emailService.sendPasswordResetEmail("someone@test.com", "0f9a4b6c-token", locale)

        verify(mailSender).send(captor.capture())
        return captor.firstValue
    }

    @Test
    fun `email carries the raw token`() {
        val message = sentMessage("en")

        assertTrue(message.text!!.contains("0f9a4b6c-token"))
    }

    @Test
    fun `email never contains a localhost link`() {
        val message = sentMessage("en")

        assertFalse(message.text!!.contains("localhost"), "must not still send the dead placeholder link")
    }

    @Test
    fun `no link section when no destination is configured`() {
        val message = sentMessage("en", linkBaseUrl = "")

        assertFalse(message.text!!.contains("http"))
    }

    @Test
    fun `link section appears when a destination is configured`() {
        val message = sentMessage("en", linkBaseUrl = "https://app.example.com/reset-password")

        assertTrue(message.text!!.contains("https://app.example.com/reset-password?token=0f9a4b6c-token"))
    }

    @Test
    fun `ru locale renders in Russian`() {
        val message = sentMessage("ru")

        assertTrue(message.subject!!.contains("сброс пароля"))
        assertTrue(message.text!!.contains("Здравствуйте"))
        assertTrue(message.text!!.contains("Введите этот код"))
    }

    @Test
    fun `en locale renders in English`() {
        val message = sentMessage("en")

        assertTrue(message.subject!!.contains("Password Reset"))
        assertTrue(message.text!!.contains("Hello"))
        assertTrue(message.text!!.contains("Enter this code"))
    }

    @Test
    fun `unknown locale falls back to English`() {
        val message = sentMessage("fr")

        assertTrue(message.text!!.contains("Hello"))
    }

    @Test
    fun `token sits alone on its own line, unpunctuated`() {
        val message = sentMessage("en")

        val tokenLine = message.text!!.lines().first { it.contains("0f9a4b6c-token") }
        assertEquals("0f9a4b6c-token", tokenLine.trim())
    }

    @Test
    fun `report email subject never contains a line break`() {
        val emailService = EmailService(mailSender, "from@test.com", "")
        val captor = argumentCaptor<SimpleMailMessage>()

        emailService.sendReportEmail("Recipe reported: Soup\r\nBcc: someone@else.com", listOf("line"))

        verify(mailSender).send(captor.capture())
        assertFalse(captor.firstValue.subject!!.contains('\n') || captor.firstValue.subject!!.contains('\r'))
        assertEquals("Mom's Recipes - Recipe reported: Soup Bcc: someone@else.com", captor.firstValue.subject)
    }
}
