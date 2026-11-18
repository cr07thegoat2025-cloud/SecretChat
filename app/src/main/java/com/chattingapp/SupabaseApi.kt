package com.chattingapp

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException

data class ChatSession(val accessToken: String, val refreshToken: String, val userId: String, val username: String)
data class ChatItem(val id: String, val title: String, val group: Boolean)
data class ChatMessage(val id: String, val senderId: String, val content: String, val createdAt: String)

class SupabaseApi(context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("chattingapp_session", Context.MODE_PRIVATE)
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    // Publishable keys are API keys, not JWTs; Authorization needs the anon JWT.
    private val anonJwt = BuildConfig.SUPABASE_ANON_KEY
    private val jsonType = "application/json".toMediaType()
    private var accessToken: String? = prefs.getString("access_token", null)
    private var refreshToken: String? = prefs.getString("refresh_token", null)
    var userId: String? = prefs.getString("user_id", null)
        private set
    var username: String? = prefs.getString("username", null)
        private set

    fun hasSession() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()

    @Synchronized
    fun createAccount(emailInput: String, usernameInput: String, password: String): ChatSession {
        requireConfig()
        val email = emailInput.trim().lowercase()
        val name = usernameInput.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) throw IOException("Enter a valid email address.")
        if (!name.matches(Regex("^[a-z0-9_]{3,20}$"))) throw IOException("Username must be 3–20 letters, numbers or underscores.")
        if (password.length < 8) throw IOException("Password must be at least 8 characters.")
        val body = JSONObject().put("email", email).put("password", password)
            .put("data", JSONObject().put("username", name).put("display_name", name))
        val result = request("/auth/v1/signup", body, authenticated = false)
        val session = sessionFrom(result, name)
        saveSession(session)
        return session
    }

    fun restoreSession(): Boolean {
        if (hasSession()) {
            try { request("/auth/v1/user", null, "GET"); return true } catch (_: Exception) { }
        }
        val refresh = refreshToken ?: return false
        return try {
            val result = request("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh), authenticated = false)
            val session = sessionFrom(result, username ?: "")
            saveSession(session)
            true
        } catch (_: Exception) { clearSession(); false }
    }

    fun signOut() {
        try { request("/auth/v1/logout", null, "POST") } catch (_: Exception) { }
        clearSession()
    }

    fun conversations(): List<ChatItem> {
        val uid = userId ?: throw IOException("Please sign in again.")
        val data = request("/rest/v1/conversations?select=id,title,is_group,conversation_members!inner(user_id)&conversation_members.user_id=eq.$uid&order=last_message_at.desc", null, "GET") as JSONArray
        return (0 until data.length()).map { i ->
            val item = data.getJSONObject(i)
            ChatItem(item.getString("id"), item.optString("title").ifBlank { "Conversation" }, item.optBoolean("is_group"))
        }
    }

    fun startDirectChat(friendUsername: String): String {
        val result = request("/rest/v1/rpc/start_direct_chat", JSONObject().put("friend_username", friendUsername.trim().lowercase()))
        return when (result) {
            is String -> result
            is JSONObject -> result.optString("id").ifBlank { result.optString("conversation_id") }
            else -> throw IOException("Supabase returned an unexpected conversation ID.")
        }
    }

    fun messages(conversationId: String): List<ChatMessage> {
        val data = request("/rest/v1/messages?select=id,sender_id,content,created_at&conversation_id=eq.$conversationId&order=created_at.asc&limit=200", null, "GET") as JSONArray
        return (0 until data.length()).map { i ->
            val item = data.getJSONObject(i)
            ChatMessage(item.getString("id"), item.getString("sender_id"), item.optString("content"), item.optString("created_at"))
        }
    }

    fun sendMessage(conversationId: String, content: String) {
        if (content.isBlank()) return
        val uid = userId ?: throw IOException("Please sign in again.")
        request("/rest/v1/messages", JSONObject().put("conversation_id", conversationId)
            .put("sender_id", uid).put("content", content.trim()).put("message_type", "text"), "POST",
            extraHeaders = mapOf("Prefer" to "return=minimal"))
    }

    private fun sessionFrom(value: Any, name: String): ChatSession {
        val json = value as? JSONObject ?: throw IOException("Supabase returned an invalid session response.")
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user")
        val id = user?.optString("id").orEmpty()
        if (access.isBlank() || id.isBlank()) throw IOException("Account created but no session was returned. Disable email confirmations in Supabase Authentication settings.")
        val actualName = user?.optJSONObject("user_metadata")?.optString("username").orEmpty().ifBlank { name }
        return ChatSession(access, refresh, id, actualName)
    }

    private fun saveSession(session: ChatSession) {
        accessToken = session.accessToken; refreshToken = session.refreshToken
        userId = session.userId; username = session.username
        prefs.edit().putString("access_token", accessToken).putString("refresh_token", refreshToken)
            .putString("user_id", userId).putString("username", username).apply()
    }

    private fun clearSession() {
        accessToken = null; refreshToken = null; userId = null; username = null
        prefs.edit().clear().apply()
    }

    private fun request(path: String, body: JSONObject?, method: String = "POST", authenticated: Boolean = true,
                        extraHeaders: Map<String, String> = emptyMap()): Any {
        requireConfig()
        val builder = Request.Builder().url(url + path).header("apikey", key)
            .header("Authorization", "Bearer " + if (authenticated) (accessToken ?: anonJwt) else anonJwt)
        extraHeaders.forEach { (k,v) -> builder.header(k,v) }
        val request = when (method) {
            "GET" -> builder.get().build()
            "POST" -> builder.post((body ?: JSONObject()).toString().toRequestBody(jsonType)).build()
            else -> builder.method(method, body?.toString()?.toRequestBody(jsonType)).build()
        }
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = try { val j = JSONObject(raw); j.optString("message").ifBlank { j.optString("msg").ifBlank { j.optString("error_description") } } } catch (_: Exception) { raw }
                throw IOException(detail.ifBlank { "Supabase request failed (${response.code})." })
            }
            if (raw.isBlank()) return JSONObject()
            return JSONTokener(raw).nextValue()
        }
    }

    private fun requireConfig() {
        if (url.isBlank() || key.isBlank() || anonJwt.isBlank()) throw IOException("Backend configuration missing: Supabase URL or API key is blank.")
    }
}
