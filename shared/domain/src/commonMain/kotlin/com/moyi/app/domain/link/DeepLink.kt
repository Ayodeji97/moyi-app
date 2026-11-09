package com.moyi.app.domain.link

/**
 * A link to `https://getmoyi.app` that opened the app (an Android App Link or an iOS
 * universal link), as the app understands it.
 *
 * The backend builds three, each from one configured base (moyi-backend `config/deploy.yml`,
 * `MOYI_*_LINK_BASE_URL`; the site's AASA and `assetlinks.json` claim the same paths):
 *
 * - `https://getmoyi.app/verify?token=<secret>`: the confirmation email
 *   (`VerificationEmailComposer`), [VerifyEmail].
 * - `https://getmoyi.app/reset-password?token=<secret>`: the reset email, [ResetPassword].
 * - `https://getmoyi.app/i/<CODE>`: an invite link (`InviteLinks`), [Invite].
 *
 * A token is a credential: whoever holds it can confirm the address or set the password.
 * So [toString] never prints one, and nothing that holds a link may log it.
 */
sealed interface DeepLink {
    /** The emailed confirmation link. [token] is the value after `?token=`. */
    class VerifyEmail(val token: String) : DeepLink {
        override fun equals(other: Any?): Boolean = other is VerifyEmail && other.token == token
        override fun hashCode(): Int = token.hashCode()
        override fun toString(): String = "VerifyEmail(token=<redacted>)"
    }

    /** The emailed reset link. [token] is the value after `?token=`. */
    class ResetPassword(val token: String) : DeepLink {
        override fun equals(other: Any?): Boolean = other is ResetPassword && other.token == token
        override fun hashCode(): Int = token.hashCode()
        override fun toString(): String = "ResetPassword(token=<redacted>)"
    }

    /**
     * An invite link. [code] is the last path segment, upper-cased. Whether it is a code that
     * works is the server's to say (`GET /invites/{code}`); the join screen shows its shape
     * problems and the server's answer, as for a typed code.
     */
    data class Invite(val code: String) : DeepLink

    /** Not a link the app routes: another host, another path, or a link missing its token. */
    data object Unknown : DeepLink

    companion object {
        /** The one host the app claims (the iOS entitlement and the Android intent filter). */
        const val HOST = "getmoyi.app"

        /**
         * [url] as a [DeepLink]. Pure and total: anything it does not recognise is [Unknown].
         *
         * - The scheme must be `https` and the host exactly [HOST] (any case), with no user
         *   info and no port: `www.getmoyi.app`, `getmoyi.app.evil.example` and
         *   `evil.example/?x=https://getmoyi.app/verify` are all [Unknown].
         * - `/verify` and `/reset-password` need a `token` in the query, made only of the
         *   URL-safe base64 alphabet the backend uses (letters, digits, `-`, `_`), at most
         *   [MAX_TOKEN_LENGTH] long; anything else is [Unknown] rather than a value sent on.
         * - `/i/<code>` needs one non-empty segment of letters and digits, at most
         *   [MAX_CODE_LENGTH] long.
         * - One trailing slash on the path is allowed; a fragment is ignored.
         */
        fun parse(url: String): DeepLink {
            val rest = url.trim().removePrefixIgnoringCase("https://") ?: return Unknown
            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, authorityEnd)
            if (!authority.equals(HOST, ignoreCase = true)) return Unknown

            val afterAuthority = rest.substring(authorityEnd).substringBefore('#')
            val path = afterAuthority.substringBefore('?').let { if (it.length > 1) it.removeSuffix("/") else it }
            val query = if ('?' in afterAuthority) afterAuthority.substringAfter('?') else ""

            return when {
                path == "/verify" -> queryToken(query)?.let(::VerifyEmail) ?: Unknown
                path == "/reset-password" -> queryToken(query)?.let(::ResetPassword) ?: Unknown
                path.startsWith("/i/") -> inviteCode(path.removePrefix("/i/"))?.let(::Invite) ?: Unknown
                else -> Unknown
            }
        }

        /** The backend's tokens are 32 random bytes in base64url (43 characters); this is headroom. */
        const val MAX_TOKEN_LENGTH = 128

        /** A code is six characters (states.md §2); a longer segment is not an invite. */
        const val MAX_CODE_LENGTH = 16

        private fun queryToken(query: String): String? {
            val value = query.split('&')
                .firstOrNull { it.startsWith("token=") }
                ?.removePrefix("token=")
                ?: return null
            return value.takeIf { it.isNotEmpty() && it.length <= MAX_TOKEN_LENGTH && it.all(::isTokenChar) }
        }

        private fun inviteCode(segment: String): String? =
            segment.takeIf { it.isNotEmpty() && it.length <= MAX_CODE_LENGTH && it.all { c -> c.isAsciiLetterOrDigit() } }
                ?.uppercase()

        private fun isTokenChar(c: Char): Boolean = c.isAsciiLetterOrDigit() || c == '-' || c == '_'

        private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

        private fun String.removePrefixIgnoringCase(prefix: String): String? =
            if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null
    }
}
