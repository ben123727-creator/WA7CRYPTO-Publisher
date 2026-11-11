package com.wa7crypto.publisher

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Navy = Color(0xFF101827)
private val Panel = Color(0xFF1B2638)
private val Accent = Color(0xFF21C7A8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PublisherScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublisherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("wa7_publisher", 0) }
    val scope = rememberCoroutineScope()
    var postText by remember { mutableStateOf(prefs.getString("draft", "") ?: "") }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var notice by remember { mutableStateOf("Your draft stays on this device.") }
    var showApi by remember { mutableStateOf(false) }
    var apiInput by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(!ApiKeyVault.read(context).isNullOrBlank()) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { images = it; notice = "${it.size} image(s) selected." }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel, onSurface = Color.White)) {
        Scaffold(containerColor = Navy, topBar = {
            TopAppBar(title = {
                Column {
                    Text("WA7CRYPTO", fontWeight = FontWeight.Bold)
                    Text("PUBLISHER", color = Accent, style = MaterialTheme.typography.labelSmall)
                }
            }, actions = {
                TextButton(onClick = { apiInput = ""; showApi = true }) { Text("API SETTINGS", color = Accent) }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy))
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Create a Square post", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (connected) "Square API key saved securely on this phone" else "Square API not connected", color = if (connected) Accent else Color.LightGray)
                OutlinedTextField(
                    value = postText, onValueChange = { postText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    label = { Text("Post content") }, placeholder = { Text("Write your Binance Square post...") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text("Add images") }
                    OutlinedButton(onClick = { prefs.edit().putString("draft", postText).apply(); notice = "Draft saved." }, modifier = Modifier.weight(1f)) { Text("Save draft") }
                }
                if (images.isNotEmpty()) {
                    Text("${images.size} image(s) selected", color = Accent)
                    TextButton(onClick = { images = emptyList() }) { Text("Remove images") }
                }
                Button(
                    enabled = !busy,
                    onClick = {
                        val key = ApiKeyVault.read(context)
                        if (key.isNullOrBlank()) { showApi = true; notice = "Add your Square API key first." }
                        else if (postText.isBlank()) notice = "Write your post first."
                        else {
                            busy = true; notice = "Publishing to Binance Square..."
                            scope.launch {
                                try {
                                    val link = withContext(Dispatchers.IO) { SquareApi.publish(context, key, postText, images) }
                                    notice = "Published successfully. $link"
                                    postText = ""; images = emptyList()
                                    prefs.edit().putString("draft", "").apply()
                                } catch (e: Exception) { notice = e.message ?: "Publishing failed." }
                                finally { busy = false }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "PUBLISHING..." else "PUBLISH TO BINANCE SQUARE") }
                OutlinedButton(onClick = { postText = ""; images = emptyList(); notice = "Editor cleared." }, modifier = Modifier.fillMaxWidth()) { Text("Clear editor") }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Connection", fontWeight = FontWeight.Bold)
                        Text("Square OpenAPI publishing. Text posts and up to 4 images. Your key is encrypted using Android Keystore.", color = Color.LightGray)
                    }
                }
                Text(notice, color = Accent, style = MaterialTheme.typography.bodySmall)
                Text("WA7CRYPTO Publisher • Version 1.1.0", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (showApi) {
        AlertDialog(
            onDismissRequest = { showApi = false },
            title = { Text("Binance Square API") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste your Square OpenAPI key here. It is encrypted and stored only on this phone. Never send it in chat.")
                    OutlinedTextField(value = apiInput, onValueChange = { apiInput = it }, label = { Text("Square OpenAPI key") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (connected) TextButton(onClick = { ApiKeyVault.clear(context); connected = false; notice = "Saved key removed."; apiInput = "" }) { Text("Remove saved key", color = Color.Red) }
                }
            },
            confirmButton = {
                TextButton(enabled = apiInput.isNotBlank(), onClick = {
                    ApiKeyVault.save(context, apiInput); apiInput = ""; connected = true
                    notice = "API key saved securely. It will be checked when you publish."
                    showApi = false
                }) { Text("Save securely") }
            },
            dismissButton = { TextButton(onClick = { showApi = false }) { Text("Cancel") } }
        )
    }
}
