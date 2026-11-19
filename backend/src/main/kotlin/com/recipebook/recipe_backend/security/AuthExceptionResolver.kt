package com.recipebook.recipe_backend.security

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

/**
 * Classifies the ordinary sign-up/login failures in [AuthException] as client errors
 * (`ErrorType.BAD_REQUEST`) instead of Spring GraphQL's default `INTERNAL_ERROR`, and puts the
 * stable [AuthException.code] under the error's `extensions.code` so the client can branch on it
 * without parsing English text.
 *
 * Without this resolver, every thrown exception is treated as a server crash: the real message is
 * logged and replaced with an opaque `INTERNAL_ERROR for <uuid>` in the response, which is useless
 * to a user and was exactly what showed up on a real device for "email already registered".
 */
@Component
class AuthExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        val (code, message) = when (ex) {
            is AuthException -> ex.code to ex.message
            // Also a client error, not a crash: previously surfaced as INTERNAL_ERROR, so a user
            // who mistyped their password a few times saw a meaningless failure.
            is RateLimitExceededException -> "RATE_LIMITED" to ex.message
            is InvalidInputException -> ex.code to ex.message
            else -> return null
        }
        // Some refusals carry a value the app needs to explain them (BLOCKED_WORD: which word).
        val details = (ex as? InvalidInputException)?.details ?: emptyMap()
        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.BAD_REQUEST)
            .message(message)
            .extensions(details + ("code" to code))
            .build()
    }
}
