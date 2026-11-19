package com.recipebook.recipe_backend.recipe

import com.recipebook.recipe_backend.category.Category
import com.recipebook.recipe_backend.note.RecipeNote
import com.recipebook.recipe_backend.user.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "recipes")
data class Recipe(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    /**
     * The old public/private flag. No longer decides anything: [visibility] does. Still stored,
     * and kept equal to "visibility is PUBLIC" by [applyVisibility] and [syncLegacyPublicFlag], so
     * the previous backend image reads sensible data if a deploy is rolled back.
     */
    var isPublic: Boolean = false,

    /**
     * Who may read the recipe. Change it only through [applyVisibility].
     *
     * The column has a database default so `ddl-auto: update` can add it to the populated
     * production table, and so rows inserted by the seed files or by the previous backend image
     * (neither knows the column) get a value. recipe/VisibilityBackfill then lifts the ones whose
     * `is_public` is true to PUBLIC.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    @ColumnDefault("'ONLY_ME'")
    var visibility: Visibility = if (isPublic) Visibility.PUBLIC else Visibility.ONLY_ME,

    /**
     * Set when an admin takes the recipe out of view (adminUnpublishRecipe): it may not be made
     * FAMILY or PUBLIC again until an admin lifts this. [removalReason] is what the admin wrote,
     * shown to whoever manages the recipe.
     */
    @Column(name = "publish_locked", nullable = false)
    @ColumnDefault("false")
    var publishLocked: Boolean = false,

    @Column(name = "removal_reason", columnDefinition = "TEXT")
    var removalReason: String? = null,

    @Column(name = "is_trashed", nullable = false)
    var isTrashed: Boolean = false,

    @Column(name = "copied_from_id")
    val copiedFromId: UUID? = null,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    // orphanRemoval: an ingredient taken off the list (removeIngredientFromRecipe) is deleted.
    // Without it the row stayed and the ingredient came back on the next read. Mutate this list;
    // never assign a new one, which Hibernate refuses for an orphan-removing collection.
    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val ingredients: MutableList<RecipeIngredient> = mutableListOf(),

    // A Set, not a List: a JPA list is a bag and faithfully returns duplicate join rows, so a
    // category linked twice was rendered twice ("Торты Торты"). The unique constraint stops the
    // duplicate rows existing in the first place — note ddl-auto=update will not add it to a table
    // that already exists, so deployed databases need the ALTER applied by hand.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_categories", // Matches your diagram table name
        joinColumns = [JoinColumn(name = "recipe_id")],
        inverseJoinColumns = [JoinColumn(name = "category_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["recipe_id", "category_id"])]
    )
    val categories: MutableSet<Category> = mutableSetOf(),

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "recipe_shared_users",
        joinColumns = [JoinColumn(name = "recipe_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["recipe_id", "user_id"])]
    )
    val sharedWith: MutableSet<User> = mutableSetOf(),

    @OneToMany(mappedBy = "recipe", cascade = [CascadeType.ALL], orphanRemoval = true)
    val notes: MutableList<RecipeNote> = mutableListOf(),

    @ElementCollection
    @CollectionTable(name = "recipe_photos", joinColumns = [JoinColumn(name = "recipe_id")])
    @Column(name = "url", columnDefinition = "TEXT")
    val photoUrls: MutableList<String> = mutableListOf(),

    @ElementCollection
    @CollectionTable(name = "recipe_video_links", joinColumns = [JoinColumn(name = "recipe_id")])
    @Column(name = "url", columnDefinition = "TEXT")
    val videoLinks: MutableList<String> = mutableListOf()
) {
    /** Sets who may read the recipe, and the legacy flag with it. */
    fun applyVisibility(value: Visibility) {
        visibility = value
        isPublic = value == Visibility.PUBLIC
    }

    // A safety net for anything that sets `visibility` directly: the two columns must never
    // disagree in the database.
    @PrePersist
    @PreUpdate
    fun syncLegacyPublicFlag() {
        isPublic = visibility == Visibility.PUBLIC
    }
}
