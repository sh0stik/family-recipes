package com.recipebook.recipe_backend.recipe

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Brings `recipes.visibility` into line with the older `recipes.is_public` at every start.
 *
 * `ddl-auto: update` adds the column with the default ONLY_ME, so on the first start after the
 * upgrade every existing recipe is "Only me" until this lifts the public ones to PUBLIC. Nothing
 * becomes more visible than it was: a recipe that was not public stays ONLY_ME.
 *
 * It also runs after the seed files (which only know `is_public`), and it repairs the two cases a
 * rollback to the previous image can leave behind, because that image writes `is_public` alone:
 * a recipe made public there, and a PUBLIC recipe made private there. Both statements change
 * nothing once the columns agree, so running them on every start is safe.
 */
@Component
class VisibilityBackfill(private val jdbc: JdbcTemplate) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(VisibilityBackfill::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val madePublic = jdbc.update(
            "UPDATE recipes SET visibility = 'PUBLIC' WHERE is_public = true AND visibility <> 'PUBLIC'"
        )
        val madePrivate = jdbc.update(
            "UPDATE recipes SET visibility = 'ONLY_ME' WHERE is_public = false AND visibility = 'PUBLIC'"
        )
        if (madePublic + madePrivate > 0) {
            log.info("Visibility backfill: {} recipes set to PUBLIC, {} set to ONLY_ME", madePublic, madePrivate)
        }
    }
}
