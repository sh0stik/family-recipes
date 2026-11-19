package com.recipebook.recipe_backend.moderation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BlockedWordsTest {

    private val default = BlockedWords()

    @Test
    fun `finds a blocked word whatever its case and reports it as written`() {
        assertEquals("FUCK", default.firstIn("What the FUCK is this"))
        assertEquals("Сука", default.firstIn(null, "", "Сука, пересолила"))
        assertEquals("блядь", default.firstIn("суп", "ну, блядь!"))
    }

    @Test
    fun `yo and ye are the same letter`() {
        assertEquals("ёбаный", default.firstIn("ёбаный борщ"))
    }

    @Test
    fun `matches whole words only, so ordinary cooking words pass`() {
        val innocent = listOf(
            "Хачапури по-аджарски", "Хрен со свёклой", "Блины с творогом", "Сукачёв", "Суккулент",
            "Spotted dick", "Faggots in gravy", "Cock-a-leekie soup", "Chicken breast", "Meatballs",
            "Coq au vin", "Shiitake mushrooms", "Scunthorpe pie", "Assorted nuts", "Bitchin' sauce",
            "Мудрёный салат", "Пиздатый — нет такого слова в списке",
        )
        for (text in innocent) assertNull(default.firstIn(text), text)
    }

    @Test
    fun `punctuation and line breaks end a word`() {
        assertEquals("shit", default.firstIn("salt,shit;pepper"))
        assertEquals("shit", default.firstIn("line one\nshit\nline three"))
        assertEquals("shit", default.firstIn("(shit)"))
    }

    @Test
    fun `a configured list replaces the default`() {
        val custom = BlockedWords(" Кабачок , zucchini ,, ")
        assertEquals("кабачок", custom.firstIn("икра, кабачок"))
        assertEquals("Zucchini", custom.firstIn("Zucchini bread"))
        assertNull(custom.firstIn("fuck"))
    }

    @Test
    fun `off disables the check`() {
        assertNull(BlockedWords("off").firstIn("fuck"))
        assertNull(BlockedWords(" OFF ").firstIn("fuck"))
    }
}
