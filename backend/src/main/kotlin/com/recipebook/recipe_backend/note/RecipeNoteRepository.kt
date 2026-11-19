package com.recipebook.recipe_backend.note

import com.recipebook.recipe_backend.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RecipeNoteRepository : JpaRepository<RecipeNote, UUID> {
    /** Every note [user] wrote, on any recipe — used by account deletion. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RecipeNote n WHERE n.user = :user")
    fun deleteAllByAuthor(@Param("user") user: User): Int
}
