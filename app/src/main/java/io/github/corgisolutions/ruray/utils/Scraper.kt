package io.github.corgisolutions.ruray.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

object Scraper {
    private const val TAG = "RURAY_SCRAPER"

    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/zieng2/wl/main/vless_lite.txt",
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/main/WL_CIDR_RU_CHECKED.txt",
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/main/Vless-Reality-White-Lists-Rus-Mobile.txt",
        "https://raw.githubusercontent.com/igareck/vpn-configs-for-russia/main/Vless-Reality-White-Lists-Rus-Cable.txt",
        "https://raw.githubusercontent.com/SoliSpirit/v2ray-configs/main/Countries/Russia.txt"
    )

    suspend fun fetchLinks(proxyPort: Int = 0): List<String> = withContext(Dispatchers.IO) {
        val allLinks = mutableListOf<String>()
        val seenLinks = HashSet<String>()

        Log.d(TAG, "scrape port=$proxyPort")

        for ((index, source) in SOURCES.withIndex()) {
            try {
                Log.d(TAG, "[${index + 1}/${SOURCES.size}] $source")
                val links = fetchFromUrl(source, proxyPort)
                Log.d(TAG, "[${index + 1}] ${links.size} links")

                for (link in links) {
                    if (seenLinks.add(link)) {
                        allLinks.add(link)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetch $source", e)
            }
        }

        Log.d(TAG, "total ${allLinks.size} unique")
        allLinks
    }

    private fun fetchFromUrl(urlString: String, proxyPort: Int): List<String> {
        try {
            val url = URL(urlString)
            val conn = if (proxyPort > 0) {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort))
                url.openConnection(proxy)
            } else {
                url.openConnection()
            } as HttpURLConnection

            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/109.0")
            conn.setRequestProperty("Cache-Control", "no-cache")

            val responseCode = conn.responseCode
            Log.d(TAG, "$urlString $responseCode")

            if (responseCode != 200) return emptyList()

            return conn.inputStream.bufferedReader().useLines { lines ->
                lines.map { it.trim() }
                     .filter { it.isNotEmpty() && it.startsWith("vless://") }
                     .toList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "get $urlString", e)
            return emptyList()
        }
    }
}
