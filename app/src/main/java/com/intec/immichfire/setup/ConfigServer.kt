package com.intec.immichfire.setup

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.intec.immichfire.network.ImmichClient
import com.intec.immichfire.util.NetworkUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.random.Random

data class ConfigSubmission(
    val serverUrl: String,
    val apiKey: String,
    val valid: Boolean,
    val message: String,
)

class ConfigServer(
    private val appContext: Context,
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
            "/discover" -> handleDiscover(session)
            else -> notFound()
        }
    }

    /**
     * Discover Immich servers on the LAN via two mechanisms in parallel:
     *   1. mDNS / Bonjour query for `_immich._tcp.local.` (preferred — requires the Immich
     *      host to advertise, typically via avahi-publish or `avahi-daemon` + a `.service` file).
     *   2. Direct /24 subnet probe of `/api/server/ping` on well-known Immich ports.
     * Results are merged and deduplicated by URL. Each result also carries a `source` field
     * so the UI can label whether it came from mDNS or the subnet scan.
     *
     * JSON shape: `{"results":[{"url":"http://1.2.3.4:2283","name":"...","source":"mdns"}, ...]}`.
     * Default scan ports are 2283; override with `?ports=2283,80,8080`.
     */
    private fun handleDiscover(session: IHTTPSession): Response {
        val myIp = NetworkUtils.localIpv4()
            ?: return newFixedLengthResponse(
                Response.Status.OK, "application/json",
                """{"error":"no local ipv4","results":[]}"""
            )
        val parts = myIp.split(".")
        if (parts.size != 4) return newFixedLengthResponse(
            Response.Status.OK, "application/json",
            """{"error":"bad local ipv4","results":[]}"""
        )

        val ports = (session.parameters["ports"]?.firstOrNull()
            ?: "2283")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(2283) }

        Log.i("ImmichFire", "LAN discover start: ip=$myIp ports=$ports")

        // Shared, dedup-by-URL result sink. value = (name, source)
        val merged = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

        val mdnsThread = Thread({ discoverMdns(myIp, merged) }, "immichfire-mdns")
        mdnsThread.isDaemon = true
        mdnsThread.start()

        discoverSubnet(myIp, parts, ports, merged)

        // Give the mDNS scan the remainder of a ~5 s budget to finish.
        runCatching { mdnsThread.join(5_000L) }
        if (mdnsThread.isAlive) mdnsThread.interrupt()

        val body = merged.entries.joinToString(",") { (url, nv) ->
            val (name, source) = nv
            """{"url":"${escapeJson(url)}","name":"${escapeJson(name)}","source":"$source"}"""
        }
        Log.i("ImmichFire", "LAN discover done: ${merged.size} host(s)")
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"results":[$body]}""")
    }

    private fun discoverSubnet(
        myIp: String,
        parts: List<String>,
        ports: List<Int>,
        merged: MutableMap<String, Pair<String, String>>,
    ) {
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val myLast = parts[3].toIntOrNull() ?: -1
        val pool = Executors.newFixedThreadPool(64)
        try {
            val futures = (1..254)
                .filter { it != myLast }
                .flatMap { last -> ports.map { port -> last to port } }
                .map { (last, port) ->
                    pool.submit {
                        val ip = "$prefix.$last"
                        if (probeImmich(ip, port)) {
                            val url = "http://$ip:$port"
                            // mDNS source wins if both reported the same host.
                            merged.putIfAbsent(url, "Immich @ $ip:$port" to "subnet")
                        }
                    }
                }
            val deadline = System.currentTimeMillis() + 4_000L
            for (f in futures) {
                val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                runCatching { f.get(remaining, TimeUnit.MILLISECONDS) }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * mDNS probe: ask for `_immich._tcp.local.` directly, and as a fallback also walk any
     * `_http._tcp.local.` services whose name contains "immich". JmDNS is bound to the
     * TV's own Wi-Fi/Ethernet address so the OS routes multicast through the right NIC.
     * A WifiManager MulticastLock is held for the duration of the query, so Wi-Fi power
     * saving doesn't drop the incoming multicast responses.
     */
    private fun discoverMdns(myIp: String, merged: MutableMap<String, Pair<String, String>>) {
        val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("immichfire-mdns")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        var jmdns: JmDNS? = null
        try {
            val localAddr = runCatching { InetAddress.getByName(myIp) as? Inet4Address }.getOrNull()
            jmdns = if (localAddr != null) JmDNS.create(localAddr, "immichfire") else JmDNS.create()

            val infos = buildList {
                addAll(runCatching { jmdns!!.list("_immich._tcp.local.", 3_500L).toList() }
                    .getOrDefault(emptyList()))
                // Fallback: Immich installs that advertise generic HTTP with "immich" in the name.
                runCatching { jmdns!!.list("_http._tcp.local.", 2_000L) }.getOrNull()?.forEach {
                    if (it.name?.contains("immich", ignoreCase = true) == true) add(it)
                }
            }
            for (info in infos) acceptMdnsService(info, merged)
        } catch (e: Throwable) {
            Log.w("ImmichFire", "mDNS discovery failed: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { jmdns?.close() }
            runCatching { lock?.release() }
        }
    }

    private fun acceptMdnsService(
        info: ServiceInfo,
        merged: MutableMap<String, Pair<String, String>>,
    ) {
        val host = info.inet4Addresses?.firstOrNull()?.hostAddress ?: return
        val port = info.port.takeIf { it in 1..65535 } ?: return
        val scheme = info.getPropertyString("scheme")?.takeIf { it.isNotBlank() } ?: "http"
        val pathRaw = info.getPropertyString("path").orEmpty().trim()
        val path = when {
            pathRaw.isBlank() -> ""
            pathRaw.startsWith("/") -> pathRaw.trimEnd('/')
            else -> "/" + pathRaw.trimEnd('/')
        }
        val url = "$scheme://$host:$port$path"
        val displayName = info.name?.takeIf { it.isNotBlank() } ?: "Immich @ $host"
        // mDNS is authoritative over the subnet probe when both find the same URL — replace it.
        merged[url] = displayName to "mdns"
    }

    private fun probeImmich(ip: String, port: Int): Boolean {
        val target = "http://$ip:$port/api/server/ping"
        return try {
            val u = URL(target)
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 350
            c.readTimeout = 700
            c.instanceFollowRedirects = false
            c.requestMethod = "GET"
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            val ok = if (code == 200) {
                val body = runCatching { c.inputStream.bufferedReader().use { it.readText() } }
                    .getOrDefault("")
                body.contains("\"res\"") && body.contains("pong", ignoreCase = true)
            } else false
            c.disconnect()
            ok
        } catch (_: Throwable) {
            false
        }
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

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
          .inlineRow{display:flex;gap:8px;align-items:stretch;margin-top:6px}
          .inlineRow input{flex:1}
          .inlineRow button{margin:0;width:auto;padding:0 14px;font-size:14px}
          #status{margin-top:12px;font-size:14px;min-height:20px}
          .found{margin-top:8px;display:flex;flex-direction:column;gap:6px}
          .found button{margin:0;width:100%;padding:10px;font-size:14px;background:#263;color:#cfc;text-align:left}
        </style></head><body>
          <h1>ImmichFire Setup</h1>
          <p>Enter your Immich details below. The pairing code is shown on the TV.</p>
          <form method="POST" action="/submit" id="f">
            <label>Pairing code (6 digits)</label>
            <input name="code" inputmode="numeric" pattern="\d{6}" required>
            <label>Immich Server URL</label>
            <div class="inlineRow">
              <input name="url" id="url" type="url" placeholder="http://192.168.0.100:2283" required>
              <button type="button" class="secondary" onclick="scan()">Scan LAN</button>
            </div>
            <div id="found" class="found"></div>
            <div style="font-size:12px;color:#888;margin-top:6px">Scan uses mDNS (<code>_immich._tcp.local.</code>) and a /24 subnet probe in parallel.</div>
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
            async function scan(){
              const s=document.getElementById('status');
              const found=document.getElementById('found');
              found.innerHTML='';
              s.textContent='Scanning local network…';
              s.style.color='#ccc';
              try{
                const r=await fetch('/discover');
                const j=await r.json();
                const items=j.results||[];
                if(items.length===0){ s.textContent='No Immich servers found on LAN.'; s.style.color='#f66'; return; }
                s.textContent='Found '+items.length+' server(s) — tap one to use it:';
                s.style.color='#6c6';
                items.sort((a,b)=>{
                  if(a.source===b.source) return a.url.localeCompare(b.url);
                  return a.source==='mdns'?-1:1; // mDNS results first
                });
                for(const it of items){
                  const b=document.createElement('button');
                  b.type='button';
                  const badge=it.source==='mdns'?'[mDNS] ':'[scan] ';
                  b.textContent=badge+it.url+'  —  '+it.name;
                  b.onclick=()=>{ document.getElementById('url').value=it.url; s.textContent='Using '+it.url; };
                  found.appendChild(b);
                }
              } catch(e) { s.textContent='Scan failed: '+e; s.style.color='#f66'; }
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
