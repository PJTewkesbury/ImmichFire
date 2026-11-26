package com.intec.immichfire.ui.setup

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichLogo
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

    Box(Modifier.fillMaxSize().background(ImmichColors.Background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
            ImmichLogo()
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // QR card
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(ImmichColors.Surface)
                            .border(1.dp, ImmichColors.PrimaryBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Image(
                            bitmap = qr,
                            contentDescription = null,
                            modifier = Modifier
                                .size(320.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(url, color = ImmichColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }

                // Instructions card
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ImmichColors.Surface)
                        .border(1.dp, ImmichColors.PrimaryBorder, RoundedCornerShape(14.dp))
                        .padding(28.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "Set up ImmichFire",
                            color = ImmichColors.Primary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "On your phone or laptop, open the URL shown — or scan the QR code. " +
                                "You'll be asked for a pairing code, your Immich URL, and an API key.",
                            color = ImmichColors.OnSurfaceSecondary,
                            fontSize = 17.sp,
                        )
                        Spacer(Modifier.height(8.dp))

                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ImmichColors.PrimarySubtle)
                                .border(1.dp, ImmichColors.PrimaryBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                        ) {
                            Column {
                                Text(
                                    "Pairing code",
                                    color = ImmichColors.Primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    code,
                                    color = ImmichColors.Primary,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        lastMessage?.let {
                            Text(it, color = ImmichColors.OnSurfaceSecondary, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}
