package com.intec.immichfire.setup

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.intec.immichfire.core.Constants
import com.intec.immichfire.util.NetworkUtils
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * LAN discovery: find Immich servers via mDNS (`_immich._tcp.local.`) and a /24 subnet probe
 * in parallel. Pulled out of [ConfigServer] so the HTTP router doesn't share a file with
 * thread pools, multicast locks, and the mDNS fallback walk.
 *
 * Results are keyed by URL in [merged]; mDNS wins over the subnet probe for the same host.
 */
internal class LanDiscovery(private val appContext: Context) {

    data class Result(val url: String, val name: String, val source: String)

    fun discover(ports: List<Int>): List<Result> {
        val myIp = NetworkUtils.localIpv4() ?: return emptyList()
        val parts = myIp.split(".")
        if (parts.size != 4) return emptyList()

        Log.i("ImmichFire", "LAN discover start: ip=$myIp ports=$ports")

        // Shared, dedup-by-URL result sink. value = (name, source)
        val merged = ConcurrentHashMap<String, Pair<String, String>>()

        val mdnsThread = Thread({ discoverMdns(myIp, merged) }, "immichfire-mdns")
        mdnsThread.isDaemon = true
        mdnsThread.start()

        discoverSubnet(parts, ports, merged)

        // Give mDNS the remainder of a bounded budget to finish.
        runCatching { mdnsThread.join(Constants.Discovery.MDNS_TOTAL_BUDGET_MS) }
        if (mdnsThread.isAlive) mdnsThread.interrupt()

        Log.i("ImmichFire", "LAN discover done: ${merged.size} host(s)")
        return merged.map { (url, nv) -> Result(url, nv.first, nv.second) }
    }

    private fun discoverSubnet(
        parts: List<String>,
        ports: List<Int>,
        merged: MutableMap<String, Pair<String, String>>,
    ) {
        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        val myLast = parts[3].toIntOrNull() ?: -1
        val pool = Executors.newFixedThreadPool(Constants.Discovery.SUBNET_THREAD_POOL_SIZE)
        try {
            val futures = (1..254)
                .filter { it != myLast }
                .flatMap { last -> ports.map { port -> last to port } }
                .map { (last, port) ->
                    pool.submit {
                        val ip = "$prefix.$last"
                        if (probeImmich(ip, port)) {
                            val url = "http://$ip:$port"
                            merged.putIfAbsent(url, "Immich @ $ip:$port" to "subnet")
                        }
                    }
                }
            val deadline = System.currentTimeMillis() + Constants.Discovery.SUBNET_PROBE_TOTAL_BUDGET_MS
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
     * `_http._tcp.local.` services whose name contains "immich". JmDNS is bound to the TV's
     * own Wi-Fi/Ethernet address so the OS routes multicast through the right NIC. A
     * WifiManager MulticastLock is held for the duration so Wi-Fi power saving doesn't drop
     * the incoming responses.
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
                addAll(runCatching {
                    jmdns!!.list("_immich._tcp.local.", Constants.Discovery.MDNS_IMMICH_LIST_TIMEOUT_MS).toList()
                }.getOrDefault(emptyList()))
                runCatching {
                    jmdns!!.list("_http._tcp.local.", Constants.Discovery.MDNS_HTTP_LIST_TIMEOUT_MS)
                }.getOrNull()?.forEach {
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
        // mDNS is authoritative over the subnet probe — replace any existing subnet entry.
        merged[url] = displayName to "mdns"
    }

    private fun probeImmich(ip: String, port: Int): Boolean {
        val target = "http://$ip:$port/api/server/ping"
        return try {
            val u = URL(target)
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = Constants.Discovery.SUBNET_PROBE_CONNECT_TIMEOUT_MS
            c.readTimeout = Constants.Discovery.SUBNET_PROBE_READ_TIMEOUT_MS
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
}
