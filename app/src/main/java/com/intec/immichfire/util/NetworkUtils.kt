package com.intec.immichfire.util

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun localIpv4(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
