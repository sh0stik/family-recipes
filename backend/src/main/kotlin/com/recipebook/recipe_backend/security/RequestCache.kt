package com.recipebook.recipe_backend.security

import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

/**
 * Remembers a value for the rest of the current HTTP request.
 *
 * Access rules are asked once per recipe by field resolvers (`Recipe.canEdit` on a list of a few
 * hundred recipes), and some of them need a lookup that is the same for every recipe in the
 * request — which recipes the caller edits, whom they blocked. This turns hundreds of identical
 * queries into one. Outside a request (unit tests, start-up code) nothing is remembered and the
 * value is simply computed.
 */
@Component
class RequestCache {

    fun <T : Any> get(key: String, compute: () -> T): T {
        val attributes = RequestContextHolder.getRequestAttributes() ?: return compute()
        @Suppress("UNCHECKED_CAST")
        val cached = attributes.getAttribute(PREFIX + key, RequestAttributes.SCOPE_REQUEST) as T?
        if (cached != null) return cached
        return compute().also { attributes.setAttribute(PREFIX + key, it, RequestAttributes.SCOPE_REQUEST) }
    }

    /** Forgets everything remembered for this request. Call after a mutation changes what was looked up. */
    fun clear() {
        val attributes = RequestContextHolder.getRequestAttributes() ?: return
        attributes.getAttributeNames(RequestAttributes.SCOPE_REQUEST)
            .filter { it.startsWith(PREFIX) }
            .forEach { attributes.removeAttribute(it, RequestAttributes.SCOPE_REQUEST) }
    }

    private companion object {
        const val PREFIX = "recipe.requestCache."
    }
}
