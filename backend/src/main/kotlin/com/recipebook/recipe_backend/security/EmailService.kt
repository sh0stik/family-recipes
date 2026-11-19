package com.recipebook.recipe_backend.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.username}") private val fromEmail: String,
    /**
     * Base URL of a web frontend or deep link that can consume a reset token, e.g.
     * "https://app.example.com/reset-password". Left blank by default: there is no deployed web
     * frontend, and the app itself only ever has the user paste the token in by hand (see
     * ResetPasswordScreen in the client). Blank means the email carries the token alone, with no
     * link section at all - do not hardcode a URL here.
     */
    @Value("\${recipe.password-reset.link-base-url:}") private val resetLinkBaseUrl: String,
    /** Where reports of recipes and users go (moderation/ModerationController). */
    @Value("\${recipe.moderation.report-email:reports@example.com}")
    private val reportEmail: String = "reports@example.com",
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    /**
     * Tells the moderation address about a report. In the background and never thrown: the report
     * is already stored (admins can read it with the `reports` query), so a mail server that is
     * down or slow must neither fail the user's report nor make them wait.
     */
    @Async
    fun sendReportEmail(subject: String, lines: List<String>) {
        try {
            val message = SimpleMailMessage()
            message.from = fromEmail
            message.setTo(reportEmail)
            // A recipe name can contain line breaks, and a line break in a header starts a new header.
            message.subject = "Mom's Recipes - ${subject.replace(Regex("\\s+"), " ").trim()}"
            message.text = lines.joinToString("\n")
            mailSender.send(message)
        } catch (e: Exception) {
            log.warn("Could not send the report email \"{}\": {}", subject, e.message)
        }
    }

    /**
     * Sent in the background. Sending synchronously made requestPasswordReset reveal which emails
     * have accounts: a known address waited for the mail server (or failed with INTERNAL_ERROR when
     * it was down), an unknown one answered at once. A failed send is logged, not reported.
     */
    @Async
    fun sendPasswordResetEmail(toEmail: String, token: String, locale: String) {
        val message = SimpleMailMessage()
        message.from = fromEmail
        message.setTo(toEmail)

        val link = buildResetLink(token)
        val texts = if (locale == "ru") RU else EN

        message.subject = texts.subject
        message.text = texts.body(token, link)

        mailSender.send(message)
    }

    private fun buildResetLink(token: String): String? {
        if (resetLinkBaseUrl.isBlank()) return null
        val separator = if (resetLinkBaseUrl.contains("?")) "&" else "?"
        return "$resetLinkBaseUrl${separator}token=$token"
    }

    /**
     * Deliberately not routed through the `translation/` module - that table translates
     * per-entity fields (recipe names, notes) keyed by entity id, which doesn't fit two fixed
     * email templates. A plain locale switch is simpler and correct for this case.
     */
    private class Texts(
        val subject: String,
        private val greeting: String,
        private val intro: String,
        private val instruction: String,
        private val linkIntro: String,
        private val ignoreNote: String,
        private val expiryNote: String
    ) {
        fun body(token: String, link: String?): String {
            // Built as a line list rather than a trimIndent()'d raw string: the token/link are
            // interpolated at runtime, and mixing dynamic multi-line content into a raw string
            // defeats trimIndent()'s margin detection (it looks at the final string's lines, so
            // an interpolated line with no leading whitespace drops the common indent to zero
            // and stops the static lines from being trimmed).
            val lines = mutableListOf(
                greeting,
                "",
                intro,
                "",
                instruction,
                "",
                token
            )
            if (link != null) {
                lines += ""
                lines += linkIntro
                lines += ""
                lines += link
            }
            lines += ""
            lines += ignoreNote
            lines += expiryNote
            return lines.joinToString("\n")
        }
    }

    companion object {
        private val EN = Texts(
            subject = "Recipe App - Password Reset Request",
            greeting = "Hello,",
            intro = "You recently requested to reset your password for your Recipe App account.",
            instruction = "Enter this code on the \"Reset Password\" screen in the app:",
            linkIntro = "You can also open this link:",
            ignoreNote = "If you did not request a password reset, please ignore this email.",
            expiryNote = "This code will expire in 1 hour."
        )

        private val RU = Texts(
            subject = "Recipe App — сброс пароля",
            greeting = "Здравствуйте,",
            intro = "Вы запросили сброс пароля для вашего аккаунта в Recipe App.",
            instruction = "Введите этот код на экране «Сброс пароля» в приложении:",
            linkIntro = "Также вы можете перейти по ссылке:",
            ignoreNote = "Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо.",
            expiryNote = "Код действителен в течение 1 часа."
        )
    }
}
