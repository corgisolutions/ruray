package io.github.corgisolutions.ruray.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

object UpdateDownloader {

    fun downloadApk(
        context: Context,
        url: String,
        socksPort: Int
    ): Flow<DownloadState> = flow {
        try {
            val client = OkHttpClient.Builder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(DownloadState.Error("download failed with code ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(DownloadState.Error("empty response"))
                return@flow
            }

            val totalBytes = body.contentLength()
            val file = File(context.cacheDir, "update.apk")

            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        emit(DownloadState.Progress(bytesDownloaded, totalBytes))
                    }
                }
            }

            emit(DownloadState.Success(file))

        } catch (e: Exception) {
            emit(DownloadState.Error(e.message ?: "unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}