package com.intec.immichfire.ui.setup

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.data.SettingsRepository
import com.intec.immichfire.network.ImmichClient
import com.intec.immichfire.setup.ConfigServer
import com.intec.immichfire.setup.ConfigSubmission
import com.intec.immichfire.util.NetworkUtils
import com.intec.immichfire.util.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(
    repo: SettingsRepository,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val localIp = remember { NetworkUtils.localIpv4() }
    val url = remember(localIp) { "http://${localIp ?: "???"}:8080" }
    var server by remember { mutableStateOf<ConfigServer?>(null) }
    var done by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = ConfigServer { submittedUrl, submittedKey ->
            val pingResult = runCatching { ImmichClient.create(submittedUrl, submittedKey).ping() }
            pingResult.onFailure { Log.e("ImmichFire", "Ping to $submittedUrl failed", it) }
            val ok = pingResult.isSuccess
            if (ok) {
                repo.saveServer(submittedUrl, submittedKey)
                lastMessage = "Configured and saved."
                done = true
                ConfigSubmission(submittedUrl, submittedKey, true, "Saved. Return to the TV.")
            } else {
                lastMessage = "Could not reach Immich server."
                ConfigSubmission(submittedUrl, submittedKey, false, "Could not reach Immich. Check URL and API key.")
            }
        }
        val startResult = withContext(Dispatchers.IO) {
            runCatching { s.start(0, false) }
        }
        startResult.onFailure { e ->
            Log.e("ImmichFire", "ConfigServer.start failed", e)
            lastMessage = "Setup server failed: ${e.javaClass.simpleName}: ${e.message}"
        }.onSuccess {
            Log.i("ImmichFire", "ConfigServer started. listening=${s.wasStarted()} url=$url ip=$localIp")
        }
        server = s
    }
    DisposableEffect(Unit) {
        onDispose { server?.stop() }
    }

    LaunchedEffect(done) {
        if (done) onDone()
    }

    val qr = remember(url) { QrCode.encode(url) }
    val code = server?.pairingCode ?: "------"

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        Row(
            Modifier.fillMaxSize().padding(48.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = qr,
                    contentDescription = null,
                    modifier = Modifier.size(360.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(url, color = Color.White, fontSize = 20.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Set up ImmichFire", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(
                    "On your phone or laptop, open the URL shown — or scan the QR code. " +
                        "You'll be asked for a pairing code, your Immich URL, and an API key.",
                    color = Color(0xFFCCCCCC), fontSize = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text("Pairing code", color = Color(0xFF888888), fontSize = 14.sp)
                Text(code, color = Color(0xFFFF6600), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                lastMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xFFAAAAAA), fontSize = 16.sp)
                }
            }
        }
    }
}
