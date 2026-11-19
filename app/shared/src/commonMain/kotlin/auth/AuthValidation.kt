package auth

/**
 * Minimum password length, enforced wherever a password is *set* — registration and password
 * reset. Must stay in step with `AuthService.MIN_PASSWORD_LENGTH` on the backend: if this is
 * lower, the form accepts a password the server then refuses, and the error the client receives
 * is masked into something generic the user cannot act on.
 *
 * Deliberately not applied on the sign-in screen. The rule governs choosing a password, not
 * typing an existing one, and an account created before the rule may hold a shorter password.
 *
 * There is no companion rule about which characters are allowed. Whitespace is valid in a
 * password, and passwords are never trimmed — only the identity fields around them are.
 */
const val MIN_PASSWORD_LENGTH = 8
