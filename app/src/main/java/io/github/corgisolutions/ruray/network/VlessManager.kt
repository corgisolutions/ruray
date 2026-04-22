package io.github.corgisolutions.ruray.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.github.corgisolutions.ruray.Config
import io.github.corgisolutions.ruray.utils.VlessParser
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.Collections

class VlessManager(private val context: Context) {

    private val processLock = Any()
    private var persistentProcess: Process? = null
    private val allocatedPorts = Collections.synchronizedSet(mutableSetOf<Int>())

    fun allocatePort(): Int {
        repeat(100) {
            val port = Config.PORT_RANGE.random()
            if (allocatedPorts.add(port) && isPortAvailable(port)) {
                return port
            }
            allocatedPorts.remove(port)
        }
        return Config.PORT_RANGE.random()
    }

    fun releasePort(port: Int) {
        allocatedPorts.remove(port)
    }

    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun testHost(link: String, timeoutMs: Int = Config.LATENCY_TIMEOUT_MS): Long {
        val port = allocatePort()
        try {
            val json = VlessParser.parseVlessUrlToConfig(link, port) ?: return -1L
            return checkLatency(json, port, timeoutMs)
        } finally {
            releasePort(port)
        }
    }

    suspend fun checkLatency(configContent: String, localPort: Int, timeoutMs: Int = Config.LATENCY_TIMEOUT_MS): Long = withContext(Dispatchers.IO) {
        val libPath = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!libPath.exists()) return@withContext -1L

        copyAssets()

        val configFile = File(context.cacheDir, "test_$localPort.json")
        configFile.writeText(configContent)

        var process: Process? = null
        try {
            val pb = ProcessBuilder(libPath.absolutePath, "run", "-c", configFile.absolutePath)
            pb.directory(context.cacheDir)
            pb.redirectErrorStream(true)
            pb.environment()["XRAY_LOCATION_ASSET"] = context.filesDir.absolutePath

            process = pb.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            Thread {
                try {
                    @Suppress("ControlFlowWithEmptyBody")
                    while (reader.readLine() != null) { }
                } catch (_: Exception) {}
            }.start()

            delay(1000)
            if (!process.isAlive) return@withContext -1L

            val start = System.currentTimeMillis()
            val success = checkLocalProxyAlive(localPort, timeoutMs)
            val end = System.currentTimeMillis()

            return@withContext if (success) (end - start) else -1L
        } catch (_: Exception) {
            return@withContext -1L
        } finally {
            process?.destroyForcibly()
            try { configFile.delete() } catch (_: Exception) {}
        }
    }

    fun startProxyForever(configContent: String, localPort: Int = Config.DEFAULT_SOCKS_PORT) {
        val libPath = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        val configFile = File(context.filesDir, "config_persistent.json")

        synchronized(processLock) {
            stopProxyInternal()
            configFile.writeText(configContent)

            try {
                val pb = ProcessBuilder(libPath.absolutePath, "run", "-c", configFile.absolutePath)
                pb.directory(context.filesDir)
                pb.redirectErrorStream(true)
                pb.environment()["XRAY_LOCATION_ASSET"] = context.filesDir.absolutePath

                persistentProcess = pb.start()

                val stream = persistentProcess?.inputStream
                if (stream != null) {
                    val reader = BufferedReader(InputStreamReader(stream))
                    Thread {
                        var line: String?
                        try {
                            while (reader.readLine().also { line = it } != null) {
                                Log.d(TAG, "xray: $line")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "xray log: ${e.message}")
                        }
                    }.start()
                }

                Log.d(TAG, "xray up port=$localPort")
            } catch (e: Exception) {
                Log.e(TAG, "xray start", e)
            }
        }
    }

    fun stopProxy() {
        synchronized(processLock) {
            stopProxyInternal()
        }
    }

    private fun stopProxyInternal() {
        persistentProcess?.destroyForcibly()
        persistentProcess = null
        Log.d(TAG, "xray down")
    }

    fun isRunning(): Boolean {
        synchronized(processLock) {
            return persistentProcess?.isAlive == true
        }
    }

    suspend fun checkLocalProxyAlive(port: Int, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        synchronized(processLock) {
            if (persistentProcess != null && !persistentProcess!!.isAlive) {
                return@withContext false
            }
        }

        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
            socket.soTimeout = timeoutMs

            val out = socket.getOutputStream()
            val ins = socket.getInputStream()

            out.write(byteArrayOf(0x05, 0x01, 0x00)) 
            out.flush()
            val buffer = ByteArray(2)
            var totalRead = 0
            while (totalRead < 2) {
                val r = ins.read(buffer, totalRead, 2 - totalRead)
                if (r == -1) return@withContext false
                totalRead += r
            }
            if (buffer[0] != 0x05.toByte() || buffer[1] != 0x00.toByte()) return@withContext false

            val domain = "cp.cloudflare.com"
            val domainBytes = domain.toByteArray()
            val request = ByteBuffer.allocate(4 + 1 + domainBytes.size + 2)
            request.put(byteArrayOf(0x05, 0x01, 0x00, 0x03))
            request.put(domainBytes.size.toByte())
            request.put(domainBytes)
            request.putShort(80)
            out.write(request.array())
            out.flush()

            val respHeader = ByteArray(4)
            totalRead = 0
            while (totalRead < 4) {
                val r = ins.read(respHeader, totalRead, 4 - totalRead)
                if (r == -1) return@withContext false
                totalRead += r
            }
            if (respHeader[0] != 0x05.toByte() || respHeader[1] != 0x00.toByte()) return@withContext false

            val atyp = respHeader[3].toInt() and 0xFF
            val skipBytes = when (atyp) {
                0x01 -> 4 + 2
                0x03 -> {
                    val len = ins.read()
                    if (len == -1) return@withContext false
                    len + 2
                }
                0x04 -> 16 + 2
                else -> return@withContext false
            }

            val skipBuf = ByteArray(skipBytes)
            totalRead = 0
            while (totalRead < skipBytes) {
                val r = ins.read(skipBuf, totalRead, skipBytes - totalRead)
                if (r == -1) return@withContext false
                totalRead += r
            }

            val httpReq = "GET /generate_204 HTTP/1.1\r\nHost: $domain\r\nUser-Agent: Mozilla/5.0\r\nConnection: close\r\n\r\n".toByteArray()
            out.write(httpReq)
            out.flush()

            val reader = BufferedReader(InputStreamReader(ins))
            val statusLine = reader.readLine()
            if (statusLine != null && statusLine.startsWith("HTTP/")) {
                val parts = statusLine.split(" ")
                if (parts.size >= 2) {
                    val code = parts[1].toIntOrNull()
                    if (code == 204) {
                        return@withContext true
                    }
                }
            }
            return@withContext false

        } catch (e: Exception) {
            Log.w(TAG, "alive check: ${e.message}")
            false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun copyAssets() {
        val files = listOf("geoip.dat", "geosite.dat")
        val targetDir = context.filesDir
        files.forEach { fileName ->
            val file = File(targetDir, fileName)
            if (!file.exists()) {
                try {
                    context.assets.open(fileName).use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "RURAY_APP"
    }
}
