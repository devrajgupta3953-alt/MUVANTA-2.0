package com.example.data.remote

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class SupabaseSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtSeconds: Long,
) {
    fun isExpiredOrNearExpiry(nowSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        expiresAtSeconds <= nowSeconds + 60L
}

data class SignUpOutcome(
    val email: String,
    val requiresEmailConfirmation: Boolean,
)

class SupabaseAuthService(context: Context) {

    private val sessionStore = SecureSessionStore(context.applicationContext)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val _session = MutableStateFlow<SupabaseSession?>(sessionStore.load())
    val session: StateFlow<SupabaseSession?> = _session.asStateFlow()

    val isConfigured: Boolean
        get() = SupabaseConfig.isConfigured

    private fun ensureSafeConfiguration(): String? {
        SupabaseConfig.securityWarning?.let { return it }
        if (!SupabaseConfig.isConfigured) {
            return "Supabase is not configured in AI Studio Secrets yet."
        }
        return null
    }

    suspend fun signUp(email: String, password: String): Result<SignUpOutcome> = withContext(Dispatchers.IO) {
        ensureSafeConfiguration()?.let { return@withContext Result.failure(IllegalStateException(it)) }
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail) || password.length < MIN_SIGNUP_PASSWORD_LEN) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a valid email and a password of at least $MIN_SIGNUP_PASSWORD_LEN characters."),
            )
        }
        runCatching {
            val bodyJson = JSONObject().apply {
                put("email", cleanEmail)
                put("password", password)
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.baseUrl}/auth/v1/signup")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseSupabaseError(raw, "Sign up failed (${response.code})."))
                }
                // Intentionally do NOT persist or activate any session on sign-up (no auto-login).
                val parsed = parseSessionFromJson(raw)
                SignUpOutcome(
                    email = cleanEmail,
                    requiresEmailConfirmation = parsed == null,
                )
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<SupabaseSession> =
        signInWithPassword(email, password)

    suspend fun signInWithPassword(email: String, password: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        ensureSafeConfiguration()?.let { return@withContext Result.failure(IllegalStateException(it)) }
        val cleanEmail = email.trim()
        if (!isValidEmail(cleanEmail) || password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your email and password."))
        }
        runCatching {
            val bodyJson = JSONObject().apply {
                put("email", cleanEmail)
                put("password", password)
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.baseUrl}/auth/v1/token?grant_type=password")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseSupabaseError(raw, "Sign in failed (${response.code})."))
                }
                val parsed = parseSessionFromJson(raw)
                    ?: throw IllegalStateException("Supabase did not return an active session.")
                sessionStore.save(parsed)
                _session.value = parsed
                parsed
            }
        }
    }

    suspend fun getSession(isOnline: Boolean = true): SupabaseSession? = withContext(Dispatchers.IO) {
        val current = _session.value ?: sessionStore.load()?.also { _session.value = it } ?: return@withContext null
        if (current.userId.isBlank() || current.accessToken.isBlank()) {
            clearLocalSession()
            return@withContext null
        }
        if (!current.isExpiredOrNearExpiry()) {
            return@withContext current
        }
        if (!isOnline) {
            return@withContext current
        }
        refreshSession(current.refreshToken).getOrNull()
    }

    suspend fun ensureFreshAccessToken(): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        ensureSafeConfiguration()?.let { return@withContext Result.failure(IllegalStateException(it)) }
        val current = _session.value
            ?: return@withContext Result.failure(IllegalStateException("Not signed in."))
        if (!current.isExpiredOrNearExpiry()) {
            return@withContext Result.success(current)
        }
        refreshSession(current.refreshToken)
    }

    suspend fun refreshSession(refreshToken: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        ensureSafeConfiguration()?.let { return@withContext Result.failure(IllegalStateException(it)) }
        if (refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Session expired. Please sign in again."))
        }
        runCatching {
            val bodyJson = JSONObject().apply { put("refresh_token", refreshToken) }
            val request = Request.Builder()
                .url("${SupabaseConfig.baseUrl}/auth/v1/token?grant_type=refresh_token")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 400 || response.code == 401) {
                        clearLocalSession()
                    }
                    throw IllegalStateException("Session expired. Please sign in again.")
                }
                val parsed = parseSessionFromJson(raw)
                    ?: throw IllegalStateException("Could not refresh session.")
                sessionStore.save(parsed)
                _session.value = parsed
                parsed
            }
        }
    }

    suspend fun signOutRemoteBestEffort() = withContext(Dispatchers.IO) {
        val current = _session.value
        if (current != null && SupabaseConfig.isConfigured) {
            runCatching {
                val request = Request.Builder()
                    .url("${SupabaseConfig.baseUrl}/auth/v1/logout")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer ${current.accessToken}")
                    .post("{}".toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().close()
            }
        }
        clearLocalSession()
    }

    fun clearLocalSession() {
        sessionStore.clear()
        _session.value = null
    }

    private fun isValidEmail(email: String): Boolean {
        val atIdx = email.indexOf('@')
        val dotIdx = email.lastIndexOf('.')
        return atIdx > 0 && dotIdx > atIdx + 1 && dotIdx < email.length - 1 && !email.any { it.isWhitespace() }
    }

    private fun parseSessionFromJson(raw: String): SupabaseSession? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val sessionObj = root.optJSONObject("session") ?: root
        val access = sessionObj.optString("access_token", "").trim()
        val refresh = sessionObj.optString("refresh_token", "").trim()
        val user = sessionObj.optJSONObject("user") ?: root.optJSONObject("user") ?: return null
        val uid = user.optString("id", "").trim()
        val email = user.optString("email", "").trim()
        val expiresAt = sessionObj.optLong("expires_at", 0L).takeIf { it > 0L }
            ?: ((System.currentTimeMillis() / 1000L) + sessionObj.optLong("expires_in", 3600L))
        if (access.isBlank() || uid.isBlank()) return null
        return SupabaseSession(
            userId = uid,
            email = email,
            accessToken = access,
            refreshToken = refresh,
            expiresAtSeconds = expiresAt,
        )
    }

    private fun parseSupabaseError(raw: String, fallback: String): String {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return fallback
        return obj.optString("error_description")
            .ifBlank { obj.optString("msg") }
            .ifBlank { obj.optString("message") }
            .ifBlank { fallback }
    }

    companion object {
        private const val MIN_SIGNUP_PASSWORD_LEN = 8
    }
}
