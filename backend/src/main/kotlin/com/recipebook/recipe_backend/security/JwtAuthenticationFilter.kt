package com.recipebook.recipe_backend.security

import io.jsonwebtoken.Claims
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: CustomUserDetailsService,
    private val blacklistedTokenRepository: BlacklistedTokenRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)
        if (blacklistedTokenRepository.existsByToken(jwt)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token is blacklisted")
            return
        }

        // Verifies the signature and the expiry; a token failing either is treated as no token.
        val claims = try {
            jwtService.extractAllVerifiedClaims(jwt)
        } catch (e: Exception) {
            filterChain.doFilter(request, response)
            return
        }
        val issuedAt = claims.issuedAt?.toInstant()
        if (issuedAt == null) {
            filterChain.doFilter(request, response)
            return
        }

        if (SecurityContextHolder.getContext().authentication == null) {
            val userDetails = when (val owner = ownerOf(claims, issuedAt)) {
                Owner.Anonymous -> {
                    filterChain.doFilter(request, response)
                    return
                }
                Owner.Refused -> {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token no longer valid")
                    return
                }
                is Owner.Found -> owner.details
            }
            val user = userDetails.user

            // A suspended account is stopped at once, not when its 30-day token runs out.
            if (user.suspended) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Account suspended")
                return
            }

            // Reject tokens issued before the password was last changed. Without this a stolen
            // token stays usable for the rest of its lifetime even after the victim changes
            // their password, which defeats the point of changing it. Tokens are only second-
            // resolution, so allow a second of slack to avoid rejecting the token that
            // changePassword itself just issued.
            val changedAt = user.passwordChangedAt
            if (changedAt != null && issuedAt.isBefore(changedAt.minusSeconds(1))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token no longer valid")
                return
            }

            if (jwtService.isTokenValid(jwt, userDetails)) {
                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        }
        filterChain.doFilter(request, response)
    }

    private sealed interface Owner {
        /** The request goes on as a guest's, as for a request without a token. */
        data object Anonymous : Owner
        /** Answered 401: the token was issued to an earlier account with the same username. */
        data object Refused : Owner
        data class Found(val details: CustomUserDetails) : Owner
    }

    /**
     * The account the token was issued to.
     *
     * New tokens carry the account id, and the account is found by it. The id never changes, so a
     * token keeps working after its account is renamed, and it can never open another account
     * that registers the old name. An id with no account behind it (deleted) makes the request a
     * guest's, as a deleted account's token always has.
     *
     * Tokens issued before the id was added (up to 30 days old) name the account only by username,
     * so those are looked up by name, as before: a name with no account makes the request a
     * guest's, and a token older than the account itself cannot be its own (it was issued to an
     * earlier account with that name, since deleted or renamed), so it is refused. Compared in
     * whole seconds because `iat` has no milliseconds and sign-up issues a token in the same
     * second the account is created.
     */
    private fun ownerOf(claims: Claims, issuedAt: Instant): Owner {
        val userId = try {
            jwtService.userIdOf(claims)
        } catch (e: IllegalArgumentException) {
            return Owner.Refused
        }
        if (userId != null) {
            return userDetailsService.loadUserById(userId)?.let { Owner.Found(it) } ?: Owner.Anonymous
        }

        val username = claims.subject ?: return Owner.Anonymous
        val details = try {
            userDetailsService.loadUserByUsername(username) as CustomUserDetails
        } catch (e: UsernameNotFoundException) {
            return Owner.Anonymous
        }
        val createdAt = details.user.createdAt ?: return Owner.Found(details)
        val olderThanAccount = issuedAt.truncatedTo(ChronoUnit.SECONDS)
            .isBefore(createdAt.truncatedTo(ChronoUnit.SECONDS))
        return if (olderThanAccount) Owner.Refused else Owner.Found(details)
    }
}
