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
        val params = formParams(session)
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
}
