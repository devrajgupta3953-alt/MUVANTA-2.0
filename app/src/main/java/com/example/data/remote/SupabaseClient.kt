package com.example.data.remote

import android.util.Base64
import com.example.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/**
 * Centralized Supabase connection configuration for MUVANTA (Android).
 *
 * Security model:
 * - Credentials are injected at build time via BuildConfig from AI Studio's Secrets panel
 *   (mapped from .env.example) and are never hardcoded in source files.
 * - ONLY Supabase publishable/anon keys are permitted in the Android client; privileged
 *   `service_role` or `sb_secret_` keys are explicitly detected and rejected at runtime so
 *   admin credentials can never be transmitted from an APK.
 * - `SUPABASE_URL` must be a valid public HTTPS endpoint (cleartext HTTP, embedded URL
 *   credentials, and private/loopback addresses are rejected).
 */
object SupabaseConfig {
    private val normalizedBaseUrl: String
        get() {
            var url = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
            if (url.endsWith("/rest/v1", ignoreCase = true)) {
                url = url.dropLast("/rest/v1".length).trimEnd('/')
            } else if (url.endsWith("/auth/v1", ignoreCase = true)) {
                url = url.dropLast("/auth/v1".length).trimEnd('/')
            }
            return url
        }

    val baseUrl: String
        get() = normalizedBaseUrl

    val restUrl: String
        get() = "$normalizedBaseUrl/rest/v1"

    val authUrl: String
        get() = "$normalizedBaseUrl/auth/v1"

    private val rawSelectedKey: String
        get() {
            val publishable = BuildConfig.SUPABASE_PUBLIC_KEY.trim()
            if (publishable.isNotBlank() && !isPlaceholder(publishable)) return publishable
            val legacyAnon = BuildConfig.SUPABASE_ANON_KEY.trim()
            if (legacyAnon.isNotBlank() && !isPlaceholder(legacyAnon)) return legacyAnon
            return ""
        }

    /**
     * Returns a non-null security error message if the configured credentials violate
     * client safety rules (e.g., a privileged `service_role`/`sb_secret_` key or non-HTTPS URL).
     */
    val securityWarning: String?
        get() {
            val rawKey = rawSelectedKey
            if (rawKey.isNotEmpty() && isPrivilegedSecretKey(rawKey)) {
                return "Blocked: A privileged Supabase secret/service_role key was detected in client config. Replace it with your publishable (anon) key in AI Studio Secrets."
            }
            val url = normalizedBaseUrl
            if (url.isNotBlank() && !url.contains("YOUR_SUPABASE_PROJECT_URL", ignoreCase = true)) {
                if (!isValidPublicHttpsBaseUrl(url)) {
                    return "Blocked: SUPABASE_URL must be a valid public HTTPS URL."
                }
            }
            return null
        }

    val publicKey: String
        get() {
            val candidate = rawSelectedKey
            return if (isValidClientKey(candidate)) candidate else ""
        }

    val anonKey: String
        get() = publicKey

    val isConfigured: Boolean
        get() = securityWarning == null &&
            isValidPublicHttpsBaseUrl(normalizedBaseUrl) &&
            isValidClientKey(rawSelectedKey)

    private fun isPlaceholder(value: String): Boolean =
        value.contains("YOUR_SUPABASE_PUBLIC_KEY", ignoreCase = true) ||
            value.contains("YOUR_SUPABASE_ANON_KEY", ignoreCase = true)

    private fun isValidClientKey(value: String): Boolean =
        value.length >= 20 &&
            !isPlaceholder(value) &&
            !isPrivilegedSecretKey(value)

    /**
     * Detects privileged Supabase keys (`sb_secret_...` or JWTs whose payload specifies
     * `"role": "service_role"` / `"supabase_admin"`) that must NEVER ship in a mobile client.
     */
    internal fun isPrivilegedSecretKey(key: String): Boolean {
        val trimmed = key.trim()
        if (trimmed.startsWith("sb_secret_", ignoreCase = true)) return true
        val parts = trimmed.split(".")
        if (parts.size == 3 && parts[0].startsWith("eyJ")) {
            val payloadJson = runCatching {
                val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                JSONObject(String(decoded, Charsets.UTF_8))
            }.getOrNull()
            val role = payloadJson?.optString("role", "")?.lowercase().orEmpty()
            if (role == "service_role" || role == "supabase_admin") {
                return true
            }
        }
        return false
    }

    internal fun isValidPublicHttpsBaseUrl(url: String): Boolean {
        if (url.isBlank() || url.contains("YOUR_SUPABASE_PROJECT_URL", ignoreCase = true)) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return false
        return MetadataService.isPublicHost(parsed.host)
    }
}
