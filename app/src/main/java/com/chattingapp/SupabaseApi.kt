package com.chattingapp

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SupabaseApi {
    private val client = OkHttpClient()
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val jsonType = "application/json".toMediaType()

    fun authenticate(username: String, password: String, create: Boolean) {
        requireConfig()
        if (!username.matches(Regex("^[a-z0-9_]{3,20}$"))) throw IOException("Username must be 3–20 letters, numbers or underscores.")
        if (password.length < 8) throw IOException("Password must be at least 8 characters.")
        val email = "$username@chattingapp.invalid"
        val payload = JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("username", username).put("display_name", username))
        val path = if (create) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val body = if (create) payload else JSONObject().put("email", email).put("password", password)
        val request = Request.Builder().url(url + path)
            .header("apikey", key).header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try {
                    val json = JSONObject(raw)
                    json.optString("msg").ifBlank { json.optString("message").ifBlank { json.optString("error_description") } }
                } catch (_: Exception) { raw }
                throw IOException(detail.ifBlank { "Authentication failed (${response.code})." })
            }
            if (!create && JSONObject(raw).optString("access_token").isBlank()) {
                throw IOException("No session returned. Check Supabase Auth email-confirmation settings.")
            }
        }
    }

    private fun requireConfig() {
        if (url.isBlank() || key.isBlank()) throw IOException("Backend configuration is missing. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY as GitHub Actions variables.")
    }
}
