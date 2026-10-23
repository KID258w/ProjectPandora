package com.projectpandora.app.feature.account

import android.os.Build
import java.net.InetAddress
import java.net.URI

object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isRequired(apiBaseUrl: String, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        if (sdkInt < ANDROID_17_API) return false

        val host = runCatching { URI(apiBaseUrl).host }
            .getOrNull()
            ?.trim('[', ']')
            ?.lowercase()
            ?: return false

        if (host == "localhost" || host.endsWith(".local")) return true

        if (host.contains(':')) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
            val firstByte = address.address.firstOrNull()?.toInt()?.and(0xff) ?: return false
            return address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
                (address.address.size == 16 && (firstByte and 0xfe) == 0xfc)
        }

        val octets = host.split('.')
        if (octets.size != 4) return false
        val (first, second, third, fourth) = octets.map { it.toIntOrNull() ?: return false }
        if (listOf(first, second, third, fourth).any { it !in 0..255 }) return false

        return first == 10 || first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254) ||
            (first == 100 && second in 64..127)
    }

    private const val ANDROID_17_API = 37
}
