package com.recipebook.recipe_backend.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Throttles the unauthenticated mutations — login, sign-up and password reset — which are the
 * only endpoints reachable without a token and therefore the only ones worth brute-forcing or
 * abusing to send mail.
 *
 * Deliberately dependency-free and in-memory: this runs as a single instance on a small server, so
 * a ConcurrentHashMap of fixed windows is sufficient. If the backend is ever scaled to more than
 * one replica the counters become per-instance and this needs replacing with a shared store.
 *
 * Counters are held in memory only, so a restart clears them. That is acceptable — an attacker
 * cannot force a restart, and the alternative (persisting attempt counts) costs a database write
 * on every login.
 */
@Service
class RateLimitService {

    private data class Window(val startedAt: Instant, val count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /**
     * Records one attempt against [key]. Throws when more than [limit] attempts occur within
     * [window]. The message is deliberately vague: it should not reveal whether the underlying
     * account or email exists.
     */
    fun check(key: String, limit: Int, window: Duration) {
        val now = Instant.now()
        // Keys embed caller-supplied text (usernames, emails). Capping the length bounds the
        // memory one attacker can make this map hold.
        val updated = windows.compute(key.take(MAX_KEY_LENGTH)) { _, existing ->
            if (existing == null || Duration.between(existing.startedAt, now) > window) {
                Window(now, 1)
            } else {
                existing.copy(count = existing.count + 1)
            }
        }!!

        if (updated.count > limit) {
            throw RateLimitExceededException(
                "Too many attempts. Please wait a few minutes and try again."
            )
        }
    }

    /**
     * Client address for keying.
     *
     * Production sits behind a Cloudflare Tunnel, which sets CF-Connecting-IP to the address that
     * connected to Cloudflare. X-Forwarded-For is deliberately NOT used: Cloudflare appends to a
     * client-supplied X-Forwarded-For instead of replacing it, so its first entry is whatever the
     * caller typed, and keying on it let anyone dodge every per-address limit by sending a new
     * value per request.
     *
     * Requests that reach the backend directly (the LAN port, tests) have no CF-Connecting-IP and
     * fall back to the socket address. Someone on the LAN could still set the header themselves;
     * the internet cannot, because only the tunnel reaches the backend from outside.
     */
    fun clientIp(request: HttpServletRequest): String {
        val cloudflare = request.getHeader("CF-Connecting-IP")?.trim()
        if (!cloudflare.isNullOrBlank()) return cloudflare.take(MAX_KEY_PART)
        return request.remoteAddr ?: "unknown"
    }

    /** Drops windows that can no longer deny anything, so the map cannot grow without bound. */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    fun evictExpired() {
        val cutoff = Instant.now().minus(Duration.ofHours(2))
        windows.entries.removeIf { it.value.startedAt.isBefore(cutoff) }
    }
}

private const val MAX_KEY_LENGTH = 200
private const val MAX_KEY_PART = 64

class RateLimitExceededException(message: String) : RuntimeException(message)
