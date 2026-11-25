package com.intec.immichfire.setup

import android.util.Log
import com.intec.immichfire.network.ImmichClient
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

class ConfigServer(
    port: Int = 8080,
    private val onSubmit: suspend (serverUrl: String, apiKey: String) -> ConfigSubmission,
) : NanoHTTPD(port) {

    val pairingCode: String = Random.nextInt(0, 1_000_000).toString().padStart(6, '0')
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {
            "/" -> html(indexPage())
            "/submit" -> handleSubmit(session)
            "/test" -> handleTest(session)
            "/diag" -> handleDiag(session)
            else -> notFound()
        }
    }

    private fun handleDiag(session: IHTTPSession): Response {
        val url = session.parameters["url"]?.firstOrNull().orEmpty().trim()
        val key = session.parameters["apiKey"]?.firstOrNull().orEmpty().trim()
        val target = url.trimEnd('/') + "/api/server/ping"
        val sb = StringBuilder("HttpURLConnection diag for $target\n")
        val t0 = System.currentTimeMillis()
        try {
            val u = java.net.URL(target)
            val c = u.openConnection() as javax.net.ssl.HttpsURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("Accept", "application/json")
            c.requestMethod = "GET"
            val code = c.responseCode
            val body = c.inputStream.bufferedReader().use { it.readText() }
            sb.append("code=$code elapsedMs=${System.currentTimeMillis() - t0}\n")
            sb.append("body=$body\n")
        } catch (e: Throwable) {
            sb.append("FAILED after ${System.currentTimeMillis() - t0}ms: ${e.javaClass.simpleName}: ${e.message}\n")
            Log.e("ImmichFire", "diag $target failed", e)
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", sb.toString())
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        if (session.method != Method.POST) return notFound()
        val body = readBody(session)
        val params = parseForm(body)
        val code = params["code"].orEmpty()
        val url = params["url"].orEmpty().trim()
        val key = params["apiKey"].orEmpty().trim()
        if (code != pairingCode) {
            return html(resultPage(success = false, message = "Wrong pairing code."))
        }
        val result = runBlocking {
            withTimeoutOrNull(15_000) { onSubmit(url, key) }
        } ?: ConfigSubmission(url, key, false, "Timed out contacting server.")
        return html(resultPage(result.valid, result.message))
    }

    private fun handleTest(session: IHTTPSession): Response {
        val body = readBody(session)
        val params = parseForm(body)
        val url = params["url"].orEmpty().trim()
        val key = params["apiKey"].orEmpty().trim()
        Log.i("ImmichFire", "Test ping requested url=$url keyLen=${key.length}")
        val result = runBlocking {
            withTimeoutOrNull(25_000) {
                Log.i("ImmichFire", "Calling ping() now")
                val r = runCatching { ImmichClient.create(url, key).ping() }
                Log.i("ImmichFire", "ping() returned success=${r.isSuccess}")
                r
            }
        }
        val message = when {
            result == null -> "FAIL: timed out"
            result.isSuccess -> "OK"
            else -> {
                val e = result.exceptionOrNull()
                Log.e("ImmichFire", "Test ping to $url failed", e)
                "FAIL: ${e?.javaClass?.simpleName}: ${e?.message}"
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/plain", message)
    }

    private fun indexPage(): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>ImmichFire Setup</title>
        <style>
          body{font-family:system-ui;max-width:460px;margin:24px auto;padding:0 16px;background:#111;color:#eee}
          h1{font-size:20px}
          label{display:block;margin-top:16px;font-size:14px;color:#aaa}
          input{width:100%;padding:12px;font-size:16px;border:1px solid #444;border-radius:8px;background:#222;color:#fff;box-sizing:border-box}
          button{margin-top:20px;width:100%;padding:14px;font-size:16px;border:0;border-radius:8px;background:#ff6600;color:#fff;font-weight:600}
          .secondary{background:#333}
          .row{display:flex;gap:8px}
          .row button{flex:1}
          #status{margin-top:12px;font-size:14px;min-height:20px}
        </style></head><body>
          <h1>ImmichFire Setup</h1>
          <p>Enter your Immich details below. The pairing code is shown on the TV.</p>
          <form method="POST" action="/submit" id="f">
            <label>Pairing code (6 digits)</label>
            <input name="code" inputmode="numeric" pattern="\d{6}" required>
            <label>Immich Server URL</label>
            <input name="url" type="url" placeholder="http://192.168.0.100:2283" required>
            <label>API Key</label>
            <input name="apiKey" required>
            <div id="status"></div>
            <div class="row">
              <button type="button" class="secondary" onclick="test()">Test connection</button>
              <button type="submit">Save</button>
            </div>
          </form>
          <script>
            async function test(){
              const f=document.getElementById('f');
              const fd=new FormData(f);
              const s=document.getElementById('status');
              s.textContent='Testing…';
              const r=await fetch('/test',{method:'POST',body:new URLSearchParams(fd)});
              const t=await r.text();
              s.textContent=t==='OK'?'Connected OK ✓':('✗ '+t);
              s.style.color=t==='OK'?'#6c6':'#f66';
            }
          </script>
        </body></html>
    """.trimIndent()

    private fun resultPage(success: Boolean, message: String): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>ImmichFire Setup</title>
        <style>body{font-family:system-ui;max-width:460px;margin:60px auto;padding:0 16px;background:#111;color:#eee;text-align:center}
        .ok{color:#6c6}.bad{color:#f66}a{color:#ff6600}</style></head><body>
        <h1 class="${if (success) "ok" else "bad"}">${if (success) "Saved ✓" else "Error"}</h1>
        <p>$message</p>
        ${if (!success) "<p><a href='/'>Try again</a></p>" else "<p>You can close this page and return to the TV.</p>"}
        </body></html>
    """.trimIndent()

    private fun html(body: String) =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)

    private fun notFound() =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")

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
