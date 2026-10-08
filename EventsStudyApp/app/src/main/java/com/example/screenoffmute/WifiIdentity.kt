package com.example.screenoffmute

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.nio.ByteBuffer
import java.security.MessageDigest

object WifiIdentity {
    data class Snapshot(
        val fingerprint: String,
        val description: String,
        val legacyFingerprint: String = ""
    )

    fun current(context: Context): Snapshot? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Prefer the active Wi-Fi, but fall back to another currently available
        // Wi-Fi network if a VPN/mobile network temporarily becomes active.
        val networks = buildList {
            cm.activeNetwork?.let { add(it) }
            cm.allNetworks.forEach { if (!contains(it)) add(it) }
        }
        val network = networks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null

        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val lp = cm.getLinkProperties(network) ?: return null

        val ipv4Networks = lp.linkAddresses
            .mapNotNull { networkPart(it) }
            .sorted()

        // Use only IPv4 default gateways. IPv6 temporary/privacy addresses can
        // rotate while the phone remains on exactly the same Wi-Fi.
        val gateways = lp.routes
            .filter { it.isDefaultRoute && it.hasGateway() && it.gateway is Inet4Address }
            .mapNotNull { it.gateway?.hostAddress }
            .filter { it.isNotBlank() }
            .sorted()

        val dns = lp.dnsServers
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }
            .sorted()

        if (gateways.isEmpty() && ipv4Networks.isEmpty()) return null

        // Stable identity: prefer the default IPv4 gateway because it normally
        // remains unchanged across DHCP renewals and DNS/Private-DNS changes.
        // Fall back to the IPv4 network only when no gateway is exposed.
        val stableRaw = if (gateways.isNotEmpty()) {
            listOf("wifi-v12", gateways.joinToString(",")).joinToString("|")
        } else {
            listOf("wifi-v12", ipv4Networks.joinToString(",")).joinToString("|")
        }

        // Keep the previous v10 fingerprint so existing installations can be
        // migrated automatically the first time the stable identity matches.
        val legacyRaw = listOf(
            "wifi-v10",
            ipv4Networks.joinToString(","),
            gateways.joinToString(","),
            dns.joinToString(",")
        ).joinToString("|")

        fun digest(raw: String): String = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(16)

        val desc = buildString {
            if (gateways.isNotEmpty()) append("Gateway: ${gateways.first()}")
            if (ipv4Networks.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("Network: ${ipv4Networks.first()}")
            }
        }.ifBlank { "Wi-Fi network detected" }

        Snapshot(
            fingerprint = digest(stableRaw),
            description = desc,
            legacyFingerprint = digest(legacyRaw)
        )
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
