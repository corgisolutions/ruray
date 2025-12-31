package io.github.corgisolutions.ruray.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlin.random.Random

object NetworkUtils {

    fun getSystemMtu(context: Context): Int {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return 1280
            val props: LinkProperties = cm.getLinkProperties(network) ?: return 1280
            props.mtu.takeIf { it in 1280..1500 } ?: 1280
        } catch (_: Exception) {
            1280
        }
    }

    fun generateRandomIpv4(): String {
        val a = Random.nextInt(1, 255)
        val b = Random.nextInt(0, 255)
        val c = Random.nextInt(1, 255)
        return "10.$a.$b.$c/32"
    }

    fun generateRandomIpv6(): String {
        val prefix = Random.nextInt(0, 256).toString(16).padStart(2, '0')
        val suffix = Random.nextInt(1, 0xfffe).toString(16)
        return "fd$prefix::$suffix/128"
    }

    fun parseIpv4WithPrefix(input: String): Pair<String, Int>? {
        val regex = Regex("""^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})/(\d{1,2})$""")
        val match = regex.matchEntire(input.trim()) ?: return null

        val ip = match.groupValues[1]
        val prefix = match.groupValues[2].toIntOrNull() ?: return null

        if (prefix !in 1..32) return null

        val parts = ip.split(".")
        if (parts.size != 4) return null
        for (part in parts) {
            val num = part.toIntOrNull() ?: return null
            if (num !in 0..255) return null
        }

        return ip to prefix
    }

    fun parseIpv6WithPrefix(input: String): Pair<String, Int>? {
        val parts = input.trim().split("/")
        if (parts.size != 2) return null

        val ip = parts[0]
        val prefix = parts[1].toIntOrNull() ?: return null

        if (prefix !in 1..128) return null
        if (!ip.contains(":")) return null

        val valid = if (ip.contains("::")) {
            val sides = ip.split("::")
            sides.size <= 2
        } else {
            val segs = ip.split(":")
            segs.size == 8 && segs.all { seg ->
                seg.isEmpty() || seg.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            }
        }

        return if (valid) ip to prefix else null
    }

    fun parseAddresses(input: String): Pair<Pair<String, Int>?, Pair<String, Int>?> {
        if (input.isBlank()) return null to null

        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var ipv4: Pair<String, Int>? = null
        var ipv6: Pair<String, Int>? = null

        for (part in parts) {
            if (part.contains(".") && ipv4 == null) {
                ipv4 = parseIpv4WithPrefix(part)
            } else if (part.contains(":") && ipv6 == null) {
                ipv6 = parseIpv6WithPrefix(part)
            }
        }

        return ipv4 to ipv6
    }

    fun isValidAddresses(input: String): Boolean {
        if (input.isBlank()) return true
        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return true
        if (parts.size > 2) return false

        for (part in parts) {
            val isIpv4 = part.contains(".")
            val isIpv6 = part.contains(":")
            if (!isIpv4 && !isIpv6) return false
            if (isIpv4 && parseIpv4WithPrefix(part) == null) return false
            if (isIpv6 && parseIpv6WithPrefix(part) == null) return false
        }
        return true
    }

    fun parseDnsServers(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return input.split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && isValidDnsServer(it) }
    }

    fun isValidDnsServers(input: String): Boolean {
        if (input.isBlank()) return false
        val servers = input.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (servers.isEmpty()) return false
        return servers.all { isValidDnsServer(it) }
    }

    private fun isValidDnsServer(server: String): Boolean {
        if (server.contains(".") && !server.contains(":")) {
            val parts = server.split(".")
            if (parts.size != 4) return false
            return parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
        }
        if (server.contains(":")) {
            return if (server.contains("::")) {
                server.split("::").size <= 2
            } else {
                server.split(":").size == 8
            }
        }
        return false
    }

    data class DnsPreset(
        val name: String,
        val servers: List<String>
    )

    val DNS_PRESETS = listOf(
        DnsPreset("cloudflare", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")),
        DnsPreset("google", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")),
        DnsPreset("quad9", listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")),
        DnsPreset("adguard", listOf("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"))
    )
}