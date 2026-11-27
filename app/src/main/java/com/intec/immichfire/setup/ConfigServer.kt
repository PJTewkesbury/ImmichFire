package com.intec.immichfire.setup

import android.content.Context
import android.util.Log
import com.intec.immichfire.core.Constants
import com.intec.immichfire.core.appContainer
import com.intec.immichfire.data.ApiResult
import com.intec.immichfire.data.Credentials
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import kotlin.random.Random

data class ConfigSubmission(
    val serverUrl: String,
    val apiKey: String,
    val valid: Boolean,
    val message: String,
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
    private val onSubmit: suspend (serverUrl: String, apiKey: String) -> ConfigSubmission,
) : NanoHTTPD(port) {

    val pairingCode: String = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        val params = parseForm(readBody(session))
        val code = params["code"].orEmpty()
        val url = params["url"].orEmpty().trim()
        val key = params["apiKey"].orEmpty().trim()
        if (code != pairingCode) {
            return html(SetupHtml.resultPage(success = false, message = "Wrong pairing code."))
        }
        val result = runBlocking {
            withTimeoutOrNull(15_000) { onSubmit(url, key) }
        } ?: ConfigSubmission(url, key, false, "Timed out contacting server.")
        return html(SetupHtml.resultPage(result.valid, result.message))
    }

    private fun handleTest(session: IHTTPSession): Response {
        val params = parseForm(readBody(session))
        val url = params["url"].orEmpty().trim()
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

    private fun html(body: String) =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun notFound() =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun readBody(session: IHTTPSession): String {
        val lenHeader = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (lenHeader <= 0) return ""
        val buf = ByteArray(lenHeader)
        var read = 0
        while (read < lenHeader) {
            val n = session.inputStream.read(buf, read, lenHeader - read)
            if (n <= 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun parseForm(body: String): Map<String, String> =
        body.split('&').mapNotNull {
            val i = it.indexOf('='); if (i < 0) return@mapNotNull null
            val k = URLDecoder.decode(it.substring(0, i), "UTF-8")
            val v = URLDecoder.decode(it.substring(i + 1), "UTF-8")
            k to v
        }.toMap()

    override fun stop() {
        super.stop()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
