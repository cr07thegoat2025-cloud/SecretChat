package com.chattingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Night = Color(0xFF09090B)
private val SurfaceDark = Color(0xFF15151B)
private val Violet = Color(0xFF8B5CF6)
private val Soft = Color(0xFFA1A1AA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Night, surface = SurfaceDark,
                primary = Violet, onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White)) {
                ChattingApp(SupabaseApi(applicationContext))
            }
        }
    }
}

@Composable
private fun ChattingApp(api: SupabaseApi) {
    var username by remember { mutableStateOf(api.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var chats by remember { mutableStateOf(emptyList<ChatItem>()) }
    var selected by remember { mutableStateOf<ChatItem?>(null) }
    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var draft by remember { mutableStateOf("") }
    var friendName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadChats() {
        scope.launch {
            busy = true; error = ""
            try { chats = withContext(Dispatchers.IO) { api.conversations() } }
            catch (e: Exception) { error = e.message ?: "Couldn't load chats." }
            finally { busy = false }
        }
    }

    LaunchedEffect(Unit) {
        signedIn = withContext(Dispatchers.IO) { api.restoreSession() }
        if (signedIn) loadChats()
    }

    LaunchedEffect(selected?.id) {
        val id = selected?.id ?: return@LaunchedEffect
        try { messages = withContext(Dispatchers.IO) { api.messages(id) } }
        catch (e: Exception) { error = e.message ?: "Couldn't load messages." }
    }

    if (!signedIn) {
        Box(Modifier.fillMaxSize().background(Night).padding(26.dp), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("2E", color = Violet, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text("ChattingApp", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Your people. Your conversations.", color = Soft)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") }, singleLine = true)
                OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !creating, onClick = { creating = false }, label = { Text("Log in") })
                    FilterChip(selected = creating, onClick = { creating = true }, label = { Text("Create account") })
                }
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF7777), fontSize = 13.sp)
                Button(onClick = {
                    scope.launch {
                        busy = true; error = ""
                        try {
                            withContext(Dispatchers.IO) { api.authenticate(username, password, creating) }
                            signedIn = true; password = ""; loadChats()
                        } catch (e: Exception) { error = e.message ?: "Could not connect to Supabase." }
                        finally { busy = false }
                    }
                }, enabled = !busy && username.length >= 3 && password.length >= 8,
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (creating) "Create account" else "Continue", fontWeight = FontWeight.Bold)
                }
                Text("Username and password only. No phone number or real email.", color = Soft, fontSize = 12.sp)
            }
        }
    } else if (selected != null) {
        Column(Modifier.fillMaxSize().background(Night)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null; messages = emptyList(); loadChats() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(selected!!.title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text("ChattingApp", color = Soft, fontSize = 12.sp)
                }
            }
            Divider(color = SurfaceDark)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(messages, key = { it.id }) { msg ->
                    val mine = msg.senderId == api.userId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Surface(color = if (mine) Color(0xFF5534A5) else SurfaceDark, shape = RoundedCornerShape(16.dp)) {
                            Text(msg.content, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = draft, onValueChange = { draft = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message...") }, maxLines = 4)
                IconButton(onClick = {
                    val text = draft
                    if (text.isNotBlank()) scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.sendMessage(selected!!.id, text) }
                            draft = ""; messages = withContext(Dispatchers.IO) { api.messages(selected!!.id) }
                        } catch (e: Exception) { error = e.message ?: "Message failed." }
                    }
                }) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Violet) }
            }
            if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = 16.dp), color = Color(0xFFFF7777), fontSize = 12.sp)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Night).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ChattingApp", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("@${api.username.orEmpty()}", color = Soft)
                }
                TextButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { api.signOut() }; signedIn = false; chats = emptyList() }
                }) { Text("Sign out") }
            }
            Spacer(Modifier.height(22.dp))
            Text("Start a conversation", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = friendName, onValueChange = { friendName = it.lowercase() },
                    modifier = Modifier.weight(1f), label = { Text("Friend's username") }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(enabled = friendName.length >= 3 && !busy, onClick = {
                    scope.launch {
                        busy = true; error = ""
                        try {
                            val id = withContext(Dispatchers.IO) { api.startDirectChat(friendName) }
                            friendName = ""; loadChats()
                            val item = withContext(Dispatchers.IO) { api.conversations() }.firstOrNull { it.id == id }
                            selected = item ?: ChatItem(id, "Direct chat", false)
                        } catch (e: Exception) { error = e.message ?: "Couldn't start chat." }
                        finally { busy = false }
                    }
                }) { Text("Open") }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF7777), fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your conversations", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { loadChats() }) { Text("Refresh") }
            }
            if (chats.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (busy) "Loading..." else "No conversations yet. Find someone by username above.", color = Soft)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        Surface(color = SurfaceDark, shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().clickable { selected = chat }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(chat.title, fontWeight = FontWeight.SemiBold)
                                Text(if (chat.group) "Group conversation" else "Tap to open chat", color = Soft, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
