package com.recipebook.recipe_backend.moderation

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The words a recipe may not contain once people other than its owner can read it.
 *
 * Both stores ask for a way to keep objectionable material from being posted (Apple Guideline 1.2,
 * Google's UGC policy). This is deliberately the bluntest tool that answers that: a short list of
 * swear words and slurs, matched as whole words. Whole words only, because Russian and English
 * cooking is full of innocent look-alikes — "хачапури", "spotted dick", "faggots" (a meatball
 * dish) — and a filter that refuses a real recipe is worse than one that misses a creative
 * spelling. Reports and admin removal are what actually deal with abuse.
 *
 * RECIPE_BLOCKED_WORDS (comma separated) replaces the default list without a release; the value
 * `off` switches the check off.
 */
@Component
class BlockedWords(
    @Value("\${recipe.moderation.blocked-words:}") configured: String = "",
) {
    val words: Set<String> = when {
        configured.isBlank() -> DEFAULT
        configured.trim().equals("off", ignoreCase = true) -> emptySet()
        else -> configured.split(',').map(::normalize).filter { it.isNotEmpty() }.toSet()
    }

    /** The first blocked word found in [texts], as the user wrote it, or null when they are clean. */
    fun firstIn(vararg texts: String?): String? {
        if (words.isEmpty()) return null
        for (text in texts) {
            if (text.isNullOrEmpty()) continue
            WORD.findAll(text).firstOrNull { normalize(it.value) in words }?.let { return it.value }
        }
        return null
    }

    companion object {
        // A word is a run of letters and digits in any script: punctuation, spaces and line breaks
        // all end it, so "…, сука!" is found and "сукачёв" is not.
        private val WORD = Regex("[\\p{L}\\p{N}]+")

        fun normalize(word: String): String = word.trim().lowercase().replace('ё', 'е')

        private val DEFAULT: Set<String> = listOf(
            // English
            "fuck", "fucks", "fucked", "fucker", "fucking", "motherfucker", "shit", "bullshit",
            "bitch", "bitches", "cunt", "cunts", "asshole", "assholes", "whore", "whores", "slut",
            "sluts", "nigger", "niggers", "nigga",
            // Russian
            "хуй", "хуя", "хую", "хуем", "хуи", "хуйня", "хуйню", "хуйни", "нахуй", "нахуя", "похуй",
            "хуево", "хуевый", "пизда", "пизду", "пизды", "пизде", "пиздец", "пиздеть", "пиздит",
            "ебать", "ебал", "ебала", "ебаный", "ебаная", "ебаное", "ебаные", "ебучий", "заебал",
            "заебись", "блядь", "бляди", "блядский", "блять", "сука", "суки", "суку", "мудак",
            "мудаки", "мудила", "пидор", "пидоры", "пидорас", "пидорасы", "говно", "говна", "жид",
            "жиды",
        ).map(::normalize).toSet()
    }
}
