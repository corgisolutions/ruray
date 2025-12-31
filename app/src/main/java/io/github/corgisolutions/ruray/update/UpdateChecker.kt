package io.github.corgisolutions.ruray.update

import android.content.Context
import android.util.Log
import io.github.corgisolutions.ruray.BuildConfig
import io.github.corgisolutions.ruray.utils.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val size: Long,
    val changelog: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val VERSION_URL = "https://raw.githubusercontent.com/corgisolutions/ruray/main/version.json"

    suspend fun checkForUpdate(context: Context, socksPort: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "checking for update via port $socksPort")

            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(VERSION_URL)
                .build()

            Log.d(TAG, "requesting $VERSION_URL")

            val response = client.newCall(request).execute()

            Log.d(TAG, "response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "request failed with code ${response.code}")
                return@withContext null
            }

            val body = response.body?.string()
            Log.d(TAG, "response body: $body")

            if (body == null) {
                Log.e(TAG, "empty body")
                return@withContext null
            }

            val json = JSONObject(body)
            val remoteVersionCode = json.getInt("versionCode")

            Log.d(TAG, "remote version: $remoteVersionCode, local: ${BuildConfig.VERSION_CODE}")

            if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                val lang = LocaleHelper.getLanguage(context)
                val changelog = when (lang) {
                    "ru" -> json.optString("changelog_ru", json.optString("changelog_en", ""))
                    else -> json.optString("changelog_en", json.optString("changelog_ru", ""))
                }

                val info = UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = json.getString("versionName"),
                    downloadUrl = json.getString("downloadUrl"),
                    size = json.getLong("size"),
                    changelog = changelog
                )
                Log.d(TAG, "update available: ${info.versionName}")
                info
            } else {
                Log.d(TAG, "no update needed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "check failed", e)
            null
        }
    }
}