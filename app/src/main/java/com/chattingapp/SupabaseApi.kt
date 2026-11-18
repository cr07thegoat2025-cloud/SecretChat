package com.chattingapp

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject\nimport org.json.JSONTokener
import java.io.IOException

data class ChatSession(val accessToken: String, val refreshToken: String, val userId: String, val username: String)
data class ChatItem(val id: String, val title: String, val group: Boolean)
data class ChatMessage(val id: String, val senderId: String, val content: String, val createdAt: String)

class SupabaseApi(context: Context) {
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("chattingapp_session", Context.MODE_PRIVATE)
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY
    private val jsonType = "application/json".toMediaType()
    private var accessToken: String? = prefs.getString("access_token", null)
    private var refreshToken: String? = prefs.getString("refresh_token", null)
    var userId: String? = prefs.getString("user_id", null)
        private set
    var username: String? = prefs.getString("username", null)
        private set

    fun hasSession() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()

    @Synchronized
    fun authenticate(usernameInput: String, password: String, create: Boolean): ChatSession {
        requireConfig()
        val name = usernameInput.trim().lowercase()
        if (!name.matches(Regex("^[a-z0-9_]{3,20}$"))) throw IOException("Username must be 3–20 letters, numbers or underscores.")
        if (password.length < 8) throw IOException("Password must be at least 8 characters.")
        val email = "$name@chattingapp.invalid"
        val body = JSONObject().put("email", email).put("password", password)
        if (create) body.put("data", JSONObject().put("username", name).put("display_name", name))
        val path = if (create) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val result = request(path, body, authenticated = false)
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
        val result = request("/rest/v1/rpc/start_direct_chat", JSONObject().put("friend_username", friendUsername.trim().lowercase())) as JSONObject
        return result.optString("id").ifBlank { result.optString("conversation_id") }.ifBlank { result.toString().trim('"') }
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

    private fun sessionFrom(json: JSONObject, name: String): ChatSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val user = json.optJSONObject("user")
        val id = user?.optString("id").orEmpty()
        if (access.isBlank() || id.isBlank()) throw IOException("Supabase did not return a session. In Supabase, disable Email confirmations for the Email provider so username-only accounts can sign in.")
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
            .header("Authorization", "Bearer " + if (authenticated) (accessToken ?: key) else key)
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
        if (url.isBlank() || key.isBlank()) throw IOException("Supabase configuration is missing.")
    }
}
