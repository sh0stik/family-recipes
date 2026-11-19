package com.recipebook.recipe_backend.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService {

    companion object {
        /**
         * How long a login lasts. 30 days: this is a family recipe book used from personal phones,
         * and a shorter window meant a login screen most times the app was opened. Revocation does
         * not depend on this — changing or resetting a password rejects every token issued before
         * `users.password_changed_at`, so a lost phone is handled by a password change.
         */
        private const val TOKEN_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000

        /** Claim holding the account's id. The username alone is not enough: names can be freed and reused. */
        const val USER_ID_CLAIM = "uid"
    }

    @Value("\${jwt.secret}")
    private lateinit var secretKey: String

    fun extractUsername(token: String): String {
        return extractClaim(token, Claims::getSubject)
    }

    fun generateToken(userDetails: UserDetails): String {
        return generateToken(HashMap(), userDetails)
    }

    /**
     * Every token names the account by id as well as by username, and carries a random token id.
     * The id lets JwtAuthenticationFilter refuse a token whose username now belongs to a different
     * account (deleted and re-registered, or renamed and taken). The random id keeps two logins in
     * the same second from producing the same string, so logging out one cannot block the other.
     */
    fun generateToken(extraClaims: Map<String, Any>, userDetails: UserDetails): String {
        val claims = HashMap(extraClaims)
        (userDetails as? CustomUserDetails)?.user?.id?.let { claims[USER_ID_CLAIM] = it.toString() }
        return Jwts.builder()
            .setClaims(claims)
            .setId(UUID.randomUUID().toString())
            .setSubject(userDetails.username)
            .setIssuedAt(Date(System.currentTimeMillis()))
            .setExpiration(Date(System.currentTimeMillis() + TOKEN_VALIDITY_MS))
            .signWith(getSignInKey(), SignatureAlgorithm.HS256)
            .compact()
    }

    /**
     * Whether [token] is unexpired and was issued to [userDetails]' account. A token that carries the
     * account id is matched by that id only, so it keeps working after the account is renamed; an
     * older token without one can only be matched by its username.
     */
    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val claims = try { extractAllClaims(token) } catch (e: Exception) { return false }
        val tokenUserId = claims[USER_ID_CLAIM] as? String
        val ownToken = if (tokenUserId != null) {
            tokenUserId == (userDetails as? CustomUserDetails)?.user?.id?.toString()
        } else {
            claims.subject == userDetails.username
        }
        return ownToken && !claims.expiration.before(Date())
    }

    /**
     * The account id in verified [claims], or null for a token from before the id was added (those
     * name the account only by username). Throws IllegalArgumentException if the id is malformed.
     */
    fun userIdOf(claims: Claims): UUID? = (claims[USER_ID_CLAIM] as? String)?.let(UUID::fromString)

    fun extractExpiration(token: String): Date {
        return extractClaim(token, Claims::getExpiration)
    }

    /** When the token was issued. Used to reject tokens predating a password change. */
    fun extractIssuedAt(token: String): Date {
        return extractClaim(token, Claims::getIssuedAt)
    }

    /** Parses and verifies [token]; throws if the signature or format is invalid. */
    fun extractAllVerifiedClaims(token: String): Claims = extractAllClaims(token)

    private fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(getSignInKey())
            .build()
            .parseClaimsJws(token)
            .body
    }

    private fun getSignInKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}