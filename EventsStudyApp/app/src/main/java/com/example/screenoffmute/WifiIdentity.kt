package com.example.screenoffmute

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.nio.ByteBuffer
import java.security.MessageDigest

object WifiIdentity {
    data class Snapshot(val fingerprint: String, val description: String)

    fun current(context: Context): Snapshot? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val lp = cm.getLinkProperties(network) ?: return null

        val ipv4Networks = lp.linkAddresses
            .mapNotNull { networkPart(it) }
            .sorted()

        val gateways = lp.routes
            .filter { it.isDefaultRoute && it.hasGateway() }
            .mapNotNull { it.gateway?.hostAddress }
            .filter { it.isNotBlank() }
            .sorted()

        val dns = lp.dnsServers
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }
            .sorted()

        if (gateways.isEmpty() && ipv4Networks.isEmpty()) return null

        val raw = listOf(
            "wifi-v10",
            ipv4Networks.joinToString(","),
            gateways.joinToString(","),
            dns.joinToString(",")
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val desc = buildString {
            if (gateways.isNotEmpty()) append("Gateway: ${gateways.first()}")
            if (ipv4Networks.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("Network: ${ipv4Networks.first()}")
            }
        }.ifBlank { "Wi-Fi network detected" }

        Snapshot(digest.take(16), desc)
    }.getOrNull()

    private fun networkPart(link: LinkAddress): String? {
        val addr = link.address
        if (addr !is Inet4Address) return null
        val bits = ByteBuffer.wrap(addr.address).int
        val prefix = link.prefixLength.coerceIn(0, 32)
        val mask = if (prefix == 0) 0 else -1 shl (32 - prefix)
        val net = bits and mask
        val bytes = ByteBuffer.allocate(4).putInt(net).array()
        val ip = bytes.joinToString(".") { (it.toInt() and 255).toString() }
        return "$ip/$prefix"
    }
}
