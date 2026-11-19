package com.recipebook.recipe_backend.security

import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.instrumentation.Instrumentation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Caps how expensive a single GraphQL query may be.
 *
 * The schema currently has no cycles — `Recipe.user` returns a `User`, which exposes no recipes
 * back — so unbounded nesting is not possible today. These limits exist so that adding such a
 * field later cannot silently turn one request into a database-melting query, which matters on a
 * small server where the API and Postgres share memory.
 *
 * The values are far above anything the apps send (their deepest query is roughly
 * recipe -> ingredients -> unit) and are intended to stop abuse, not to constrain the client.
 */
@Configuration
class GraphQlLimitsConfig {

    @Bean
    fun maxQueryDepthInstrumentation(): Instrumentation = MaxQueryDepthInstrumentation(12)

    @Bean
    fun maxQueryComplexityInstrumentation(): Instrumentation = MaxQueryComplexityInstrumentation(2000)
}
