package com.recipebook.recipe_backend.security

import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableMethodSecurity
class SecurityConfig(private val jwtAuthFilter: JwtAuthenticationFilter) {

    /**
     * Comma-separated list of origins the web client is allowed to call the API from. The Kotlin/JS
     * web app runs in a real browser and is subject to CORS, unlike the Android and iOS clients.
     *
     * Defaulted rather than lateinit, same reasoning as `AuthService.inviteCode`: unit tests that
     * construct this class directly never populate @Value. Defaults to the local dev web server so
     * `webApp:jsBrowserRun` works out of the box; add the production origin via
     * RECIPE_CORS_ALLOWED_ORIGINS once the web app has a public hostname. Never a wildcard — with
     * credentials off that would be safe against cookie theft, but it would still let any page on
     * the internet read responses (including recipe photos) through a visitor's browser.
     */
    @Value("\${recipe.cors.allowed-origins:http://localhost:8085}")
    private var allowedOrigins: String = "http://localhost:8085"

    @Bean
    fun passwordEncoder(): PasswordEncoder{
        return BCryptPasswordEncoder();
    }

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager {
        return config.authenticationManager
    }

    /**
     * Registered per-path (not a single catch-all glob) so each endpoint only allows the methods it
     * actually serves. `allowCredentials` stays false: the client keeps its JWT in localStorage and sends it
     * as an `Authorization` header, never a cookie, so there is nothing credentialed to protect and
     * no reason to accept the stricter (and here pointless) credentialed-CORS rules.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotBlank() }

        val graphql = CorsConfiguration()
        graphql.allowedOrigins = origins
        graphql.allowedMethods = listOf("POST", "OPTIONS")
        // Apollo sends these on every operation; without them the browser preflight fails with
        // "Request header field x-apollo-operation-id is not allowed by Access-Control-Allow-Headers"
        // and no query ever reaches the server. Native clients are unaffected — only browsers preflight.
        graphql.allowedHeaders = listOf(
            "Content-Type",
            "Authorization",
            "x-apollo-operation-id",
            "x-apollo-operation-name",
            "apollo-require-preflight",
        )
        graphql.allowCredentials = false

        // GET only: photos are read-only (see WebConfig/the /uploads permitAll comment below), and
        // Compose Multiplatform's image loader fetches them over the network on the web target
        // rather than via a plain <img> tag, so the response needs CORS headers too.
        val uploads = CorsConfiguration()
        uploads.allowedOrigins = origins
        uploads.allowedMethods = listOf("GET", "OPTIONS")
        uploads.allowedHeaders = listOf("Content-Type")
        uploads.allowCredentials = false

        // The web app uploads photos with fetch(): a multipart POST carrying the bearer token.
        val photoUpload = CorsConfiguration()
        photoUpload.allowedOrigins = origins
        photoUpload.allowedMethods = listOf("POST", "OPTIONS")
        photoUpload.allowedHeaders = listOf("Content-Type", "Authorization")
        photoUpload.allowCredentials = false

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/graphql", graphql)
        source.registerCorsConfiguration("/uploads/**", uploads)
        source.registerCorsConfiguration(PHOTO_UPLOAD_PATH, photoUpload)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Safe to disable: the API is stateless and authenticates from a bearer token in the
            // Authorization header, which a cross-site form post cannot set. No cookie or session
            // is used, so there is nothing for CSRF to ride on.
            .csrf { it.disable() }
            // Wires the bean above into the filter chain. Without this, Spring Security never adds
            // CORS response headers regardless of the bean's existence, and a browser preflight
            // still fails even though /graphql itself is permitAll for every HTTP method.
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests { auth ->
                auth
                    // Spring forwards internally to /error when a request 404s or throws. That
                    // forward is a request in its own right, so with anyRequest().denyAll() it is
                    // rejected and the caller sees 403 instead of the real status — every missing
                    // photo under /uploads would report 403 rather than 404. Permit the ERROR
                    // dispatch so error responses reach the client intact. This does not widen
                    // access: the original request was already authorized on its own merits.
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    // GraphQL is a single endpoint carrying both public and private operations,
                    // so it cannot be gated here. Every resolver carries @PreAuthorize instead;
                    // only createUser, login, requestPasswordReset and resetPassword are open.
                    .requestMatchers("/graphql").permitAll()
                    // 404s in prod: spring.graphql.graphiql.enabled defaults to false there.
                    .requestMatchers("/graphiql", "/graphiql/**").permitAll()
                    // Read-only photo serving (WebConfig). Files get here only through the upload
                    // endpoint below; this path itself only serves them.
                    .requestMatchers("/uploads/**").permitAll()
                    // Photo upload (photo/PhotoController): signed-in accounts only, POST only.
                    .requestMatchers(HttpMethod.POST, PHOTO_UPLOAD_PATH).authenticated()
                    // Liveness only. Details are hidden by default and other actuator endpoints
                    // are not exposed over HTTP.
                    .requestMatchers("/actuator/health").permitAll()
                    // Fail closed. Previously this was anyRequest().permitAll(), which meant any
                    // controller added without an annotation was publicly reachable — that is how
                    // an unauthenticated file-upload endpoint once ended up exposed. With denyAll,
                    // forgetting to classify a new endpoint makes it unreachable rather than open.
                    .anyRequest().denyAll()
            }
            .exceptionHandling { handling ->
                // A guest refused by the rules above. The upload endpoint answers 401 with a code
                // the apps map, like every other upload refusal; everything else keeps the 403 it
                // has always had (Spring's default when no login method is configured).
                handling.authenticationEntryPoint { request, response, _ ->
                    if (request.requestURI.removePrefix(request.contextPath) == PHOTO_UPLOAD_PATH) {
                        response.status = HttpStatus.UNAUTHORIZED.value()
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.writer.write("""{"code":"UNAUTHENTICATED"}""")
                    } else {
                        response.sendError(HttpStatus.FORBIDDEN.value(), "Access Denied")
                    }
                }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // 3. Put our custom JWT filter in front of the default Spring filters
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}

private const val PHOTO_UPLOAD_PATH = "/api/photos"
