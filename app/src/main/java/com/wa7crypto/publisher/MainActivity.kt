package com.wa7crypto.publisher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var postText by remember { mutableStateOf(prefs.getString("draft", "") ?: "") }
    var selectedImages by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf("Your draft stays on this device.") }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        selectedImages = uris.size
        notice = if (uris.isEmpty()) "No images selected." else "${uris.size} image(s) selected for this session."
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent,
        background = Navy,
        surface = Panel,
        onBackground = Color.White,
        onSurface = Color.White
    )) {
        Scaffold(containerColor = Navy, topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("WA7CRYPTO", fontWeight = FontWeight.Bold)
                        Text("PUBLISHER", style = MaterialTheme.typography.labelSmall, color = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy)
            )
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Create a Square post", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Write your post, save a draft, and prepare media.", color = Color.LightGray)
                OutlinedTextField(
                    value = postText,
                    onValueChange = { postText = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                    placeholder = { Text("Write your Binance Square post here...") },
                    label = { Text("Post content") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        cursorColor = Accent,
                        focusedLabelColor = Accent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add images") }
                    OutlinedButton(
                        onClick = {
                            prefs.edit().putString("draft", postText).apply()
                            notice = "Draft saved on this device."
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save draft") }
                }
                if (selectedImages > 0) {
                    Text("$selectedImages image(s) selected", color = Accent)
                }
                OutlinedButton(
                    onClick = {
                        postText = ""
                        selectedImages = 0
                        notice = "Editor cleared. Your saved draft is unchanged."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear editor") }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Publishing connection", fontWeight = FontWeight.Bold)
                        Text("Direct Binance Square publishing is not connected yet. This build prepares the editor and local drafts; API publishing will be added after the official publishing access and media requirements are verified.", color = Color.LightGray)
                    }
                }
                Text(notice, color = Accent, style = MaterialTheme.typography.bodySmall)
                Text("WA7CRYPTO Publisher • Version 1.0.0", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
