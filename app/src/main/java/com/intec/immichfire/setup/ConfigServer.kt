package com.intec.immichfire.setup

import android.content.Context
import android.util.Log
import com.intec.immichfire.core.Constants
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.data.Credentials
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

data class ConfigSubmission(
    val serverUrl: String,
    val apiKey: String,
    val valid: Boolean,
    val message: String,
)

/** Which credential path the phone chose on the setup form. */
enum class AuthMode { LOGIN, API_KEY }

/** Raw form payload passed to the submit callback — the VM picks the right flow from [mode]. */
data class ConfigRequest(
    val serverUrl: String,
    val mode: AuthMode,
    val apiKey: String = "",
    val email: String = "",
    val password: String = "",
)

/**
 * Lightweight embedded HTTP server for the phone-based setup flow. The TV shows a QR, the
 * phone POSTs here with URL + API key + pairing code. The actual Immich ping is injected
 * via [pingServer] so this file doesn't know about Retrofit/OkHttp; LAN discovery is
 * delegated to [LanDiscovery] and HTML lives in [SetupHtml].
 */
class ConfigServer(
    private val appContext: Context,
    port: Int = Constants.Network.SETUP_SERVER_PORT,
    private val pingServer: suspend (Credentials) -> ApiResult<Unit> = { cred ->
        appContext.appContainer.immichRepository.ping(cred)
    },
    private val onSubmit: suspend (ConfigRequest) -> ConfigSubmission,
) : NanoHTTPD(port) {

    val pairingCode: String = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
    private val lanDiscovery = LanDiscovery(appContext)

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/" -> html(SetupHtml.indexPage())
            "/submit" -> handleSubmit(session)
            "/test" -> handleTest(session)
            "/discover" -> handleDiscover(session)
            else -> notFound()
        }
    }

    private fun handleDiscover(session: IHTTPSession): Response {
        val ports = (session.parameters["ports"]?.firstOrNull()
            ?: Constants.Discovery.DEFAULT_IMMICH_PORT.toString())
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(Constants.Discovery.DEFAULT_IMMICH_PORT) }

        val results = lanDiscovery.discover(ports)
        val body = results.joinToString(",") {
            """{"url":"${escapeJson(it.url)}","name":"${escapeJson(it.name)}","source":"${it.source}"}"""
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"results":[$body]}""")
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        if (session.method != Method.POST) return notFound()
        val params = formParams(session)
        val code = params["code"].orEmpty()
        val url = normalizeBaseUrl(params["url"].orEmpty())
        val modeRaw = params["mode"].orEmpty().lowercase()
        val mode = if (modeRaw == "apikey") AuthMode.API_KEY else AuthMode.LOGIN
        val request = ConfigRequest(
            serverUrl = url,
            mode = mode,
            apiKey = params["apiKey"].orEmpty().trim(),
            email = params["email"].orEmpty().trim(),
            // Password is NOT trimmed; Immich allows leading/trailing spaces and we have no
            // right to silently rewrite the user's secret.
            password = params["password"].orEmpty(),
        )
        if (code != pairingCode) {
            return html(SetupHtml.resultPage(success = false, message = "Wrong pairing code."))
        }
        val result = runBlocking {
            // Login + key mint can take longer than a ping — 30 s keeps slow TLS handshakes
            // from timing out the UI loop while still bounding the request.
            withTimeoutOrNull(30_000) { onSubmit(request) }
        } ?: ConfigSubmission(url, request.apiKey, false, "Timed out contacting server.")
        return html(SetupHtml.resultPage(result.valid, result.message))
    }

    private fun handleTest(session: IHTTPSession): Response {
        val params = formParams(session)
        val url = normalizeBaseUrl(params["url"].orEmpty())
        val key = params["apiKey"].orEmpty().trim()
        Log.i("ImmichFire", "Test ping requested url=$url keyLen=${key.length}")
        val result = runBlocking {
            withTimeoutOrNull(25_000) { pingServer(Credentials(url, key)) }
        }
        val message = when (result) {
            null -> "FAIL: timed out"
            is ApiResult.Success -> "OK"
            is ApiResult.Failure -> "FAIL: ${result.error.displayMessage}"
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", message)
    }

    /**
     * Pull form-encoded parameters via NanoHTTPD's own parser. The previous hand-rolled
     * readBody() racing with NanoHTTPD's internal body consumption could return empty on
     * some NanoHTTPD builds, causing every submit to look like "Wrong pairing code." or
     * empty URL with no useful error. [NanoHTTPD.IHTTPSession.parseBody] populates
     * `session.parameters` for application/x-www-form-urlencoded POSTs.
     */
    private fun formParams(session: IHTTPSession): Map<String, String> {
        if (session.method == Method.POST) {
            runCatching { session.parseBody(HashMap<String, String>()) }
        }
        return session.parameters
            .mapValues { (_, v) -> v.firstOrNull().orEmpty() }
    }

    private fun html(body: String) =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun notFound() =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * Accept the three URL shapes the setup form advertises:
     *   https://immich.example.com     — scheme present, no port, FQDN
     *   http://192.168.0.8:2283        — scheme present, IP + port
     *   http://travelpi.lan:2283       — scheme present, hostname + port
     * plus their scheme-less siblings (`immich.example.com`, `192.168.0.8:2283`) by
     * auto-prepending `http://`. Trims trailing slashes so Retrofit's own `baseUrl` join
     * doesn't produce `//api/...` paths.
     */
    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed
        val withScheme = if (trimmed.matches(Regex("^https?://.*", RegexOption.IGNORE_CASE))) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return withScheme.trimEnd('/')
    }
}
