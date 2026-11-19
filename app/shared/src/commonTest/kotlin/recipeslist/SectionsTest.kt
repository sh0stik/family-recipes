package recipeslist

import model.Recipe
import model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals

class SectionsTest {
    private fun recipe(
        id: String,
        owner: String = "someone",
        visibility: Visibility = Visibility.PUBLIC,
        canEdit: Boolean = false,
    ) = Recipe(id = id, title = id, description = "", visibility = visibility, canEdit = canEdit, ownerName = owner)

    @Test
    fun publicIsExactlyWhatTheServerReturnedIncludingYourOwn() {
        val ownPublic = recipe("own", "me", Visibility.PUBLIC)
        val s = sectionsOf(
            mine = listOf(recipe("m1", "me", Visibility.ONLY_ME), ownPublic),
            shared = listOf(recipe("f1", visibility = Visibility.FAMILY)),
            public = listOf(ownPublic, recipe("p2")),
        )
        assertEquals(listOf("m1", "own"), s.mine.map { it.id })
        assertEquals(listOf("f1"), s.shared.map { it.id })
        assertEquals(listOf("own", "p2"), s.public.map { it.id })
    }

    @Test
    fun sharedDropsWhatIsAlreadyInMineButKeepsEditablePublicOnes() {
        val mineToo = recipe("x", "me", canEdit = true)
        val editable = recipe("p1", visibility = Visibility.PUBLIC, canEdit = true)
        val s = sectionsOf(
            mine = listOf(mineToo),
            shared = listOf(mineToo, editable, recipe("f1", visibility = Visibility.FAMILY)),
            public = listOf(mineToo, editable),
        )
        assertEquals(listOf("x"), s.mine.map { it.id })
        assertEquals(listOf("p1", "f1"), s.shared.map { it.id })
        assertEquals(listOf("x", "p1"), s.public.map { it.id })
    }

    @Test
    fun eachListShowsARecipeOnce() {
        val a = recipe("a", "me")
        val b = recipe("b", visibility = Visibility.FAMILY)
        val c = recipe("c")
        val s = sectionsOf(listOf(a, a), listOf(b, b), listOf(c, c))
        assertEquals(listOf("a"), s.mine.map { it.id })
        assertEquals(listOf("b"), s.shared.map { it.id })
        assertEquals(listOf("c"), s.public.map { it.id })
    }

    @Test
    fun searchResultsGoToTheSameThreeSections() {
        val s = classifySearch(
            listOf(
                recipe("mine", "me", Visibility.ONLY_ME),
                recipe("minePublic", "me", Visibility.PUBLIC),
                recipe("mineFamily", "me", Visibility.FAMILY),
                recipe("family", visibility = Visibility.FAMILY),
                recipe("editable", visibility = Visibility.ONLY_ME, canEdit = true),
                recipe("editablePublic", visibility = Visibility.PUBLIC, canEdit = true),
                recipe("public"),
            ),
            currentUsername = "me",
        )
        assertEquals(listOf("mine", "minePublic", "mineFamily"), s.mine.map { it.id })
        assertEquals(listOf("family", "editable", "editablePublic"), s.shared.map { it.id })
        assertEquals(listOf("minePublic", "editablePublic", "public"), s.public.map { it.id })
    }

    @Test
    fun searchShowsARecipeOncePerSection() {
        val own = recipe("own", "me", Visibility.PUBLIC)
        val s = classifySearch(listOf(own, own, recipe("p"), recipe("p")), currentUsername = "me")
        assertEquals(listOf("own"), s.mine.map { it.id })
        assertEquals(listOf("own", "p"), s.public.map { it.id })
    }

    @Test
    fun signedOutEverythingIsPublic() {
        val s = classifySearch(listOf(recipe("a"), recipe("b")), currentUsername = "")
        assertEquals(2, s.public.size)
        assertEquals(0, s.mine.size + s.shared.size)
    }

    @Test
    fun theSelectedGroupPicksItsOwnList() {
        val s = sectionsOf(listOf(recipe("m", "me")), listOf(recipe("f", visibility = Visibility.FAMILY)), listOf(recipe("p")))
        assertEquals(listOf("m"), s.of(RecipeGroup.MINE).map { it.id })
        assertEquals(listOf("f"), s.of(RecipeGroup.SHARED).map { it.id })
        assertEquals(listOf("p"), s.of(RecipeGroup.PUBLIC).map { it.id })
    }

    @Test
    fun searchWithinTheSelectedGroupOnly() {
        val results = classifySearch(listOf(recipe("mine", "me", Visibility.ONLY_ME), recipe("public")), currentUsername = "me")
        assertEquals(listOf("public"), results.of(RecipeGroup.PUBLIC).map { it.id })
        assertEquals(emptyList(), results.of(RecipeGroup.SHARED))
    }

    @Test
    fun savedGroupFallsBackToMineAndSignedOutIsPublic() {
        assertEquals(RecipeGroup.MINE, RecipeGroup.fromSaved(null))
        assertEquals(RecipeGroup.MINE, RecipeGroup.fromSaved("garbage"))
        assertEquals(RecipeGroup.SHARED, RecipeGroup.fromSaved("SHARED"))
        assertEquals(RecipeGroup.PUBLIC, RecipeGroup.effective(RecipeGroup.SHARED, isLoggedIn = false))
        assertEquals(RecipeGroup.SHARED, RecipeGroup.effective(RecipeGroup.SHARED, isLoggedIn = true))
    }
}
