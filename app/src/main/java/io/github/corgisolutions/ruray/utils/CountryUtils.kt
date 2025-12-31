package io.github.corgisolutions.ruray.utils

import android.content.Context
import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.util.Locale

object CountryUtils {
    private var databaseReader: DatabaseReader? = null
    private const val DB_NAME = "GeoLite2-Country.mmdb"

    fun init(context: Context) {
        if (databaseReader != null) return
        
        try {
            val file = File(context.filesDir, DB_NAME)
            if (!file.exists()) {
                context.assets.open(DB_NAME).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            databaseReader = DatabaseReader.Builder(file)
                .withCache(CHMCache())
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchCountryCode(address: String): String? = withContext(Dispatchers.IO) {
        if (address.isBlank() || isPrivateIp(address)) return@withContext null
        
        val reader = databaseReader ?: return@withContext null

        try {
            val inetAddress = InetAddress.getByName(address)
            val response = reader.country(inetAddress)
            return@withContext response.country.isoCode
        } catch (_: Exception) {
            null
        }
    }

    fun getFlagEmoji(countryCode: String?): String {
        if (countryCode.isNullOrEmpty() || countryCode.length != 2) return "🌐"
        val code = countryCode.uppercase(Locale.ROOT)
        // 0x1F1E6 = regional indicator symbol letter A
        val firstLetter = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
    
    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
    }
}
