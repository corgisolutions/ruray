package io.github.corgisolutions.ruray.utils

data class HostDetails(
    val address: String,
    val port: String,
    val uuid: String,
    val params: Map<String, String>
)

fun getHostDisplay(link: String): String {
    return try {
        val temp = link.substringAfter("vless://")
        val afterAt = temp.substringAfter("@", "")
        if (afterAt.isEmpty()) return "invalid"
        val addressPart = afterAt.substringBefore("?").substringBefore("#").trimEnd('/')
        addressPart.lowercase()
    } catch (_: Exception) {
        "unknown"
    }
}

fun parseVlessLink(link: String): HostDetails? {
    return try {
        val temp = link.substringAfter("vless://")
        val uuid = temp.substringBefore("@")
        val afterAt = temp.substringAfter("@")
        val hostPort = afterAt.substringBefore("?").substringBefore("#")
        val address = hostPort.substringBeforeLast(":")
        val port = hostPort.substringAfterLast(":").trimEnd('/')
        
        val query = afterAt.substringAfter("?", "").substringBefore("#")
        val params = query.split("&").associate {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to parts[1] else it to ""
        }
        HostDetails(address, port, uuid, params)
    } catch (_: Exception) {
        null
    }
}

fun areLinksEquivalent(link1: String?, link2: String?): Boolean {
    if (link1 == null || link2 == null) return false
    if (link1 == link2) return true
    
    val d1 = parseVlessLink(link1)
    val d2 = parseVlessLink(link2)
    return d1 != null && d2 != null && 
           d1.address == d2.address && 
           d1.port == d2.port && 
           d1.uuid == d2.uuid
}
