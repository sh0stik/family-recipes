package com.recipebook.recipe_backend.user

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID


@Entity
@Table(name = "users")
data class User (@Id
                 @GeneratedValue(strategy = GenerationType.UUID)
                 val id: UUID? = null,

                 @Column(nullable = false, unique = true)
                 var username: String,

                 @Column(unique = true, nullable = false)
                 val email: String,

                 @CreationTimestamp
                 @Column(name = "created_at")
                 val createdAt: Instant? = null,

                 @UpdateTimestamp
                 @Column(name = "updated_at")
                 val updatedAt: Instant? = null,

                 @Column(nullable = false)
                 var password: String = "",

                 @Column(nullable = false)
                 val role: String = "ROLE_USER",

                 @Column(name = "preferred_locale", nullable = false)
                 var preferredLocale: String = "en",

                 /**
                  * When the password was last changed. Tokens issued before this instant are
                  * rejected by JwtAuthenticationFilter, so changing or resetting a password
                  * logs out every existing session instead of leaving stolen tokens valid for
                  * the remainder of their 24h lifetime.
                  *
                  * Nullable so rows created before this column existed keep working — a null
                  * means "never changed", and no token is rejected on its account.
                  */
                 @Column(name = "password_changed_at")
                 var passwordChangedAt: Instant? = null,

                 /**
                  * When this person accepted the content rules (what may not be posted). Both app
                  * stores require that before a user's content is shown to others, so a recipe
                  * cannot be made FAMILY or PUBLIC from an account where this is null
                  * (moderation/PublishingRules). Null for every account created before the rules
                  * existed, until they accept in the app.
                  */
                 @Column(name = "terms_accepted_at")
                 var termsAcceptedAt: Instant? = null,

                 /**
                  * An account the admins stopped: it cannot sign in, its tokens are refused, and
                  * its recipes are hidden from everyone but admins. Nothing in the app sets this;
                  * it is done in the database (`UPDATE users SET suspended = true ...`). The
                  * column has a default so `ddl-auto: update` can add it to the populated table.
                  */
                 @Column(name = "suspended", nullable = false)
                 @ColumnDefault("false")
                 var suspended: Boolean = false
)