package com.chattingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
            MaterialTheme(colorScheme = darkColorScheme(
                background = Night, surface = SurfaceDark, primary = Violet,
                onPrimary = Color.White, onBackground = Color.White, onSurface = Color.White
            )) { ChattingApp() }
        }
    }
}

@Composable
private fun ChattingApp() {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (signedIn) {
        Column(Modifier.fillMaxSize().background(Night).padding(24.dp)) {
            Text("ChattingApp", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("@${username.lowercase()}", color = Soft)
            Spacer(Modifier.height(30.dp))
            Text("You're signed in.", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Your conversation inbox is coming in the next build.", color = Soft)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { signedIn = false; password = "" }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    } else {
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
                if (message.isNotBlank()) Text(message, color = Color(0xFFFF7777), fontSize = 13.sp)
                Button(
                    onClick = {
                        scope.launch {
                            busy = true; message = ""
                            try {
                                withContext(Dispatchers.IO) { SupabaseApi().authenticate(username.trim().lowercase(), password, creating) }
                                signedIn = true
                            } catch (e: Exception) { message = e.message ?: "Could not connect." }
                            finally { busy = false }
                        }
                    },
                    enabled = !busy && username.length >= 3 && password.length >= 8,
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (creating) "Create account" else "Continue", fontWeight = FontWeight.Bold)
                }
                Text("No phone number or real email address required.", color = Soft, fontSize = 12.sp)
            }
        }
    }
}
