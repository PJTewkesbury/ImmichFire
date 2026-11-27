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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.intec.immichfire.core.Constants
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.setup.ConfigServer
import com.intec.immichfire.setup.ConfigSubmission
import com.intec.immichfire.ui.theme.ImmichColors
import com.intec.immichfire.ui.theme.ImmichLogo
import com.intec.immichfire.util.NetworkUtils
import com.intec.immichfire.util.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private sealed class LocalIpState {
    data object Resolving : LocalIpState()
    data object NoNetwork : LocalIpState()
    data class Ready(val ip: String) : LocalIpState()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupScreen(
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: SetupViewModel = viewModel(
        factory = SetupViewModel.Factory(container.immichRepository, container.settingsRepository),
    )
    val appContext = remember { context.applicationContext }
    // Resolve the local IPv4 off the main thread — NetworkInterface enumeration is a
    // syscall-ish walk we don't want running during the first compose.
    var localIpState by remember { mutableStateOf<LocalIpState>(LocalIpState.Resolving) }
    LaunchedEffect(Unit) {
        val ip = withContext(Dispatchers.IO) { NetworkUtils.localIpv4() }
        localIpState = if (ip != null) LocalIpState.Ready(ip) else LocalIpState.NoNetwork
    }
    val url = (localIpState as? LocalIpState.Ready)
        ?.let { "http://${it.ip}:${Constants.Network.SETUP_SERVER_PORT}" }
    // AtomicReference so the onDispose cleanup can see the server even if the LaunchedEffect
    // that started it was cancelled before its final `server = s` assignment.
    val serverRef = remember { AtomicReference<ConfigServer?>() }
    var serverForUi by remember { mutableStateOf<ConfigServer?>(null) }
    var done by remember { mutableStateOf(false) }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    // Until we know the IP, show a minimal background rather than flashing the no-Wi-Fi
    // state at users whose Wi-Fi resolve just hasn't landed yet.
    if (localIpState is LocalIpState.Resolving) {
        Box(Modifier.fillMaxSize().background(ImmichColors.Background))
        return
    }

    // No Wi-Fi / no local IP → QR URL would be nonsense; tell the user instead.
    if (url == null) {
        Box(Modifier.fillMaxSize().background(ImmichColors.Background)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ImmichLogo()
                Spacer(Modifier.height(32.dp))
                Text(
                    "Connect to Wi-Fi",
                    color = ImmichColors.Primary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This device is not on a network. Open your Fire TV Settings → Network, " +
                        "join your Wi-Fi, then return here.",
                    color = ImmichColors.OnSurfaceSecondary,
                    fontSize = 17.sp,
                )
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        val s = ConfigServer(appContext) { submittedUrl, submittedKey ->
            when (val r = vm.verifyAndSave(submittedUrl, submittedKey)) {
                is ApiResult.Success -> {
                    lastMessage = "Configured and saved."
                    done = true
                    ConfigSubmission(submittedUrl, submittedKey, true, "Saved. Return to the TV.")
                }
                is ApiResult.Failure -> {
                    lastMessage = "Could not reach Immich server."
                    Log.e("ImmichFire", "Ping failed: ${r.error.displayMessage}")
                    ConfigSubmission(submittedUrl, submittedKey, false,
                        "Could not reach Immich. ${r.error.displayMessage}")
                }
            }
        }
        // Publish the server to the ref BEFORE start(); even if this LaunchedEffect gets
        // cancelled mid-start by a quick back-press, DisposableEffect still sees it.
        serverRef.set(s)
        serverForUi = s
        val startResult = withContext(Dispatchers.IO) {
            runCatching { s.start(0, false) }
        }
        startResult.onFailure { e ->
            Log.e("ImmichFire", "ConfigServer.start failed", e)
            // Start failed: nothing to stop, clear the ref so onDispose doesn't try.
            serverRef.compareAndSet(s, null)
            lastMessage = "Setup server failed: ${e.javaClass.simpleName}: ${e.message}"
        }.onSuccess {
            Log.i("ImmichFire", "ConfigServer started on $url")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // stop() can block briefly closing sockets; keep it off the main thread.
            val s = serverRef.getAndSet(null) ?: return@onDispose
            Thread({ runCatching { s.stop() } }, "immichfire-setup-stop").apply {
                isDaemon = true
                start()
            }
        }
    }

    LaunchedEffect(done) {
        if (done) {
            // Stop the embedded server the moment setup succeeds, not when the composable
            // happens to be disposed — otherwise the port keeps listening to other LAN hosts
            // during the handoff to the source picker.
            val s = serverRef.getAndSet(null)
            withContext(Dispatchers.IO) { runCatching { s?.stop() } }
            onDone()
        }
    }

    // QR encode on background dispatcher; pixels loop is big enough to jank the UI.
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        qr = withContext(Dispatchers.Default) { QrCode.encode(url) }
    }
    val code = serverForUi?.pairingCode ?: "------"

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
                        qr?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = "Pairing QR code for $url",
                                modifier = Modifier
                                    .size(320.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White),
                            )
                        } ?: Box(Modifier.size(320.dp).background(Color.White))
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
