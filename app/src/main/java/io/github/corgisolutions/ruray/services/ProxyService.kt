package io.github.corgisolutions.ruray.services

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.v2ray.ang.service.TProxyService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import io.github.corgisolutions.ruray.Config
import io.github.corgisolutions.ruray.MainActivity
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.data.db.AppDatabase
import io.github.corgisolutions.ruray.network.VlessManager
import io.github.corgisolutions.ruray.utils.HostUpdater
import io.github.corgisolutions.ruray.utils.LocaleHelper
import io.github.corgisolutions.ruray.utils.VlessParser
import io.github.corgisolutions.ruray.utils.getHostDisplay
import io.github.corgisolutions.ruray.utils.NetworkUtils
import java.io.File

@SuppressLint("VpnServicePolicy")
class ProxyService : VpnService() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var vlessManager: VlessManager
    private lateinit var db: AppDatabase
    private lateinit var prefs: SharedPreferences

    private var activeLink: String? = null
    @Volatile
    private var isRunning = false
    private var socksPort: Int = Config.DEFAULT_SOCKS_PORT
    private var consecutiveHostFailures = 0

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        vlessManager = VlessManager(this)
        db = AppDatabase.getDatabase(this)
        prefs = getSharedPreferences("proxy_state", MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val link = intent?.getStringExtra("LINK")
        val portExtra = intent?.getIntExtra("SOCKS_PORT", Config.DEFAULT_SOCKS_PORT) ?: Config.DEFAULT_SOCKS_PORT

        if (intent?.hasExtra("SOCKS_PORT") == true) {
            socksPort = portExtra
        }

        when (action) {
            "START" -> {
                if (link != null) {
                    activeLink = link
                    saveActiveLink(link)

                    val notification = createNotification(getString(R.string.status_connecting))
                    startForegroundSafely(notification)

                    isRunning = true

                    serviceScope.coroutineContext.cancelChildren()

                    serviceScope.launch {
                        startVpnSequence(link)
                        launch { startKeepAliveLoop() }
                        launch { startAutoUpdateLoop() }
                    }
                }
            }
            ACTION_RELOAD_TUN -> {
                 if (isRunning && activeLink != null) {
                     Log.d("RURAY_APP", "tun reload")
                     serviceScope.launch {
                         startTun(activeLink!!)
                     }
                 }
            }
            "STOP" -> {
                stopServiceComplete()
            }
            "SCAN_AND_CONNECT", SERVICE_INTERFACE -> {
                 if (!isRunning) {
                     val storedLink = prefs.getString("active_link", null)
                     isRunning = true
                     serviceScope.coroutineContext.cancelChildren()

                     if (storedLink != null) {
                         activeLink = storedLink
                         startForegroundSafely(createNotification(getString(R.string.status_connecting)))
                         serviceScope.launch {
                             startVpnSequence(storedLink)
                             launch { startKeepAliveLoop() }
                             launch { startAutoUpdateLoop() }
                         }
                     } else {
                         startForegroundSafely(createNotification(getString(R.string.notif_scanning)))
                         serviceScope.launch {
                             performScanAndConnect()
                             if (activeLink != null) {
                                 launch { startKeepAliveLoop() }
                                 launch { startAutoUpdateLoop() }
                             } else {
                                 stopServiceComplete()
                             }
                         }
                     }
                 }
            }
        }
        return START_STICKY
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } catch (_: Exception) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun performScanAndConnect() {
        updateNotification(getString(R.string.notif_scanning))
        val allHosts = db.vlessDao().getAllHostsList()
        if (allHosts.isEmpty()) {
            updateNotification(getString(R.string.status_no_hosts))
            delay(2000)
            return
        }

        val working = allHosts.filter { it.isWorking && it.latency > 0 }.sortedBy { it.latency }
        if (working.isNotEmpty()) {
            val best = working.first()
            activeLink = best.link
            saveActiveLink(best.link)
            startVpnSequence(best.link)
            return
        }

        val semaphore = Semaphore(5)
        val candidates = allHosts.take(10)

        var bestLink: String? = null
        var minLatency = Long.MAX_VALUE

        for (chunk in candidates.chunked(5)) {
             chunk.map { host ->
                serviceScope.async {
                    semaphore.withPermit {
                        val latency = vlessManager.testHost(host.link, Config.LATENCY_TIMEOUT_MS)
                        if (latency > 0) {
                            db.vlessDao().updateSuccess(host.link, true, latency, System.currentTimeMillis())
                            synchronized(this@ProxyService) {
                                if (latency < minLatency) {
                                    minLatency = latency
                                    bestLink = host.link
                                }
                            }
                        } else {
                            db.vlessDao().updateFailure(host.link, System.currentTimeMillis())
                        }
                    }
                }
             }.awaitAll()
             if (bestLink != null) break
        }

        val link = bestLink
        if (link != null) {
            activeLink = link
            saveActiveLink(link)
            startVpnSequence(link)
        } else {
             updateNotification(getString(R.string.status_no_working_hosts))
             delay(2000)
        }
    }

    private suspend fun startVpnSequence(link: String) = withContext(Dispatchers.IO) {
        try {
            stopInternalProcesses()

            startXray(link)

            val ready = withTimeoutOrNull(Config.XRAY_STARTUP_TIMEOUT_MS) {
                while (isActive) {
                    if (vlessManager.isRunning()) {
                        delay(200)
                        if (vlessManager.checkLocalProxyAlive(socksPort, 1000)) break
                    }
                    delay(100)
                }
                true
            } ?: false

            if (!ready) {
                Log.e("RURAY_APP", "xray timeout")
            }

            startTun(link)

            serviceScope.launch(Dispatchers.IO) {
                try { HostUpdater.updateHosts(db, socksPort) } catch (_: Exception) {}
            }

        } catch (e: Exception) {
            Log.e("RURAY_APP", "vpn start", e)
        }
    }

    private fun startXray(link: String) {
        val bindAddress = prefs.getString("bind_address", "127.0.0.1") ?: "127.0.0.1"
        val json = VlessParser.parseVlessUrlToConfig(link, socksPort, bindAddress)
        if (json != null) {
            Log.d("RURAY_APP", "xray $bindAddress:$socksPort")
            vlessManager.startProxyForever(json, socksPort)
        }
    }

    private suspend fun startTun(link: String) {
        val useVpn = prefs.getBoolean("vpn_mode", true)

        try { TProxyService.TProxyStopService() } catch (_: Exception) {}
        if (vpnInterface != null) {
            try { vpnInterface?.close() } catch(_: Exception) {}
            vpnInterface = null
        }

        if (!useVpn) {
            updateNotification(getString(R.string.notif_proxy_only, getHostDisplay(link)))
            return
        }

        try {
            Log.d("RURAY_APP", "tun init")

            val isKillswitchEnabled = prefs.getBoolean("killswitch", true)

            val customMtu = prefs.getString("custom_mtu", "") ?: ""
            val mtu = customMtu.toIntOrNull()?.takeIf { it in 1280..1500 }
                ?: NetworkUtils.getSystemMtu(this)

            val customAddresses = prefs.getString("custom_addresses", "") ?: ""
            val (parsedIpv4, parsedIpv6) = NetworkUtils.parseAddresses(customAddresses)

            val (ipv4Addr, ipv4Prefix) = parsedIpv4
                ?: NetworkUtils.parseIpv4WithPrefix(NetworkUtils.generateRandomIpv4())!!

            val (ipv6Addr, ipv6Prefix) = parsedIpv6
                ?: NetworkUtils.parseIpv6WithPrefix(NetworkUtils.generateRandomIpv6())!!

            Log.d("RURAY_APP", "tun mtu=$mtu ipv4=$ipv4Addr/$ipv4Prefix ipv6=$ipv6Addr/$ipv6Prefix")

            val builder = Builder()
                .setSession("RURay")
                .setMtu(mtu)
                .addAddress(ipv4Addr, ipv4Prefix)
                .addAddress(ipv6Addr, ipv6Prefix)

            val splitMode = prefs.getInt("split_mode", 1)

            if (splitMode == 2) {
                val apps = try { db.splitTunnelDao().getAllList() } catch(_: Exception) { emptyList() }
                for (app in apps) {
                    try { builder.addAllowedApplication(app) } catch (_: Exception) {}
                }
            } else {
                try { builder.addDisallowedApplication(packageName) } catch(_: Exception) {}

                if (splitMode == 0) {
                    val apps = try { db.splitTunnelDao().getAllList() } catch(_: Exception) { emptyList() }
                    for (app in apps) {
                        if (app == packageName) continue
                        try { builder.addDisallowedApplication(app) } catch (_: Exception) {}
                    }
                }
            }

            if (isKillswitchEnabled) {
                builder.addRoute("0.0.0.0", 0)
                builder.addRoute("::", 0)

                val dnsPresetIndex = prefs.getInt("dns_preset", 0)
                val dnsServers = if (dnsPresetIndex == 4) {
                    val customDns = prefs.getString("custom_dns", "") ?: ""
                    NetworkUtils.parseDnsServers(customDns).ifEmpty {
                        NetworkUtils.DNS_PRESETS[0].servers
                    }
                } else {
                    NetworkUtils.DNS_PRESETS.getOrNull(dnsPresetIndex)?.servers
                        ?: NetworkUtils.DNS_PRESETS[0].servers
                }

                for (server in dnsServers) {
                    try { builder.addDnsServer(server) } catch (_: Exception) {}
                }
            } else {
                for (route in LAN_BYPASS_ROUTES) {
                    val (ip, prefix) = route.split("/")
                    builder.addRoute(ip, prefix.toInt())
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e("RURAY_APP", "tun interface null")
                stopServiceComplete()
                return
            }

            val configFile = File(cacheDir, "hev_config.yaml")
            configFile.writeText("""
tunnel:
  mtu: $mtu
  ipv4: $ipv4Addr
  ipv6: '$ipv6Addr'
socks5:
  port: $socksPort
  address: 127.0.0.1
  udp: udp
misc:
  log-level: warn
        """.trimIndent())

            Log.d("RURAY_APP", "tproxy init")
            val fd = vpnInterface!!.fd

            Thread {
                try {
                    TProxyService.TProxyStartService(configFile.absolutePath, fd)
                } catch (e: Exception) {
                    Log.e("RURAY_APP", "tproxy", e)
                }
            }.start()

            updateNotification(getString(R.string.notif_connected, getHostDisplay(link)))

        } catch (e: Exception) {
            Log.e("RURAY_APP", "tun", e)
            stopServiceComplete()
        }
    }

    private fun stopInternalProcesses() {
        try {
            TProxyService.TProxyStopService()
        } catch (e: Exception) {
            Log.e("RURAY_APP", "tproxy stop", e)
        }
        vlessManager.stopProxy()
    }

    private fun stopServiceComplete() {
        isRunning = false
        activeLink = null
        saveActiveLink(null)

        serviceScope.coroutineContext.cancelChildren()

        stopInternalProcesses()

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun saveActiveLink(link: String?) {
        prefs.edit { putString("active_link", link) }
    }

    private suspend fun startKeepAliveLoop() {
        var failCount = 0
        while (currentCoroutineContext().isActive && isRunning) {
            try {
                delay(Config.KEEPALIVE_INTERVAL_MS)

                val currentLink = activeLink ?: continue

                var isAlive = false
                var latency = 0L

                val start = System.nanoTime()
                if (vlessManager.checkLocalProxyAlive(socksPort, 2000)) {
                    val end = System.nanoTime()
                    latency = (end - start) / 1_000_000
                    if (latency == 0L) latency = 1L
                    isAlive = true
                }

                if (activeLink != currentLink) {
                    failCount = 0
                    continue
                }

                if (isAlive) {
                    failCount = 0
                    consecutiveHostFailures = 0
                    Log.d("RURAY_PING", "$currentLink ${latency}ms")
                    db.vlessDao().updateSuccess(currentLink, true, latency, System.currentTimeMillis())

                    val useVpn = prefs.getBoolean("vpn_mode", true)
                    if (useVpn) {
                        updateNotification(getString(R.string.notif_connected, getHostDisplay(currentLink)))
                    } else {
                        updateNotification(getString(R.string.notif_proxy_only, getHostDisplay(currentLink)))
                    }
                } else {
                    failCount++
                    Log.w("RURAY_APP", "fail $failCount/${Config.KEEPALIVE_FAIL_THRESHOLD} $currentLink")

                    if (failCount >= Config.KEEPALIVE_FAIL_THRESHOLD) {
                        consecutiveHostFailures++
                        Log.w("RURAY_APP", "switching")
                        db.vlessDao().updateFailure(currentLink, System.currentTimeMillis())
                        db.vlessDao().deleteFailed(Config.HOST_FAIL_DELETE_THRESHOLD)

                        if (consecutiveHostFailures >= 3) {
                            val backoffMs = (consecutiveHostFailures * 5_000L).coerceAtMost(30_000L)
                            Log.w("RURAY_APP", "backoff ${backoffMs}ms")
                            updateNotification(getString(R.string.notif_reconnecting))
                            delay(backoffMs)
                        }

                        val nextBest = db.vlessDao().getNextBestHost(currentLink)
                        if (nextBest != null) {
                            activeLink = nextBest.link
                            saveActiveLink(nextBest.link)
                            updateNotification(getString(R.string.notif_switching, getHostDisplay(nextBest.link)))

                            startVpnSequence(nextBest.link)
                            failCount = 0
                        } else {
                            updateNotification(getString(R.string.notif_connection_lost))
                            stopServiceComplete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RURAY_APP", "keepalive", e)
            }
        }
    }

    private suspend fun startAutoUpdateLoop() {
        delay(10000)
        while (currentCoroutineContext().isActive && isRunning) {
            try {
                delay(5 * 60 * 1000)
                if (activeLink != null) {
                    Log.d("RURAY_APP", "auto-update")
                    HostUpdater.updateHosts(db, socksPort)
                }
            } catch (e: Exception) {
                Log.e("RURAY_APP", "auto-update", e)
            }
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, ProxyService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
        } else {
            null
        }
    }

    override fun onDestroy() {
        stopServiceComplete()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RELOAD_TUN = "io.github.corgisolutions.ruray.service.RELOAD_TUN"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "RURAY_CHANNEL"
        private val LAN_BYPASS_ROUTES = arrayOf(
            "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4",
            "32.0.0.0/3", "64.0.0.0/3", "96.0.0.0/6", "100.0.0.0/10", "100.128.0.0/9",
            "101.0.0.0/8", "102.0.0.0/7", "104.0.0.0/5", "112.0.0.0/5", "120.0.0.0/6",
            "124.0.0.0/7", "126.0.0.0/8", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/8",
            "169.0.0.0/9", "169.128.0.0/10", "169.192.0.0/11", "169.224.0.0/12",
            "169.240.0.0/13", "169.248.0.0/14", "169.252.0.0/15", "169.255.0.0/16",
            "170.0.0.0/7", "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10",
            "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4",
            "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16",
            "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10",
            "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5",
            "208.0.0.0/4", "240.0.0.0/5", "248.0.0.0/6", "252.0.0.0/7",
            "254.0.0.0/8", "255.0.0.0/9", "255.128.0.0/10", "255.192.0.0/11",
            "255.224.0.0/12", "255.240.0.0/13", "255.248.0.0/14", "255.252.0.0/15",
            "255.254.0.0/16", "255.255.0.0/17", "255.255.128.0/18", "255.255.192.0/19",
            "255.255.224.0/20", "255.255.240.0/21", "255.255.248.0/22", "255.255.252.0/23",
            "255.255.254.0/24", "255.255.255.0/25", "255.255.255.128/26", "255.255.255.192/27",
            "255.255.255.224/28", "255.255.255.240/29", "255.255.255.248/30", "255.255.255.252/31",
            "255.255.255.254/32",
            "::/128", "::2/127", "::4/126", "::8/125", "::10/124", "::20/123", "::40/122",
            "::80/121", "::100/120", "::200/119", "::400/118", "::800/117", "::1000/116",
            "::2000/115", "::4000/114", "::8000/113", "::1:0/112", "::2:0/111", "::4:0/110",
            "::8:0/109", "::10:0/108", "::20:0/107", "::40:0/106", "::80:0/105", "::100:0/104",
            "::200:0/103", "::400:0/102", "::800:0/101", "::1000:0/100", "::2000:0/99", "::4000:0/98",
            "::8000:0/97", "::1:0:0/96", "::2:0:0/95", "::4:0:0/94", "::8:0:0/93", "::10:0:0/92", "::20:0:0/91",
            "::40:0:0/90", "::80:0:0/89", "::100:0:0/88", "::200:0:0/87", "::400:0:0/86", "::800:0:0/85",
            "::1000:0:0/84", "::2000:0:0/83", "::4000:0:0/82", "::8000:0:0/81", "::1:0:0:0/80", "::2:0:0:0/79",
            "::4:0:0:0/78", "::8:0:0:0/77", "::10:0:0:0/76", "::20:0:0:0/75", "::40:0:0:0/74", "::80:0:0:0/73",
            "::100:0:0:0/72", "::200:0:0:0/71", "::400:0:0:0/70", "::800:0:0:0/69", "::1000:0:0:0/68", "::2000:0:0:0/67",
            "::4000:0:0:0/66", "::8000:0:0:0/65", "0:0:0:1::/64", "0:0:0:2::/63", "0:0:0:4::/62", "0:0:0:8::/61",
            "0:0:0:10::/60", "0:0:0:20::/59", "0:0:0:40::/58", "0:0:0:80::/57", "0:0:0:100::/56", "0:0:0:200::/55",
            "0:0:0:400::/54", "0:0:0:800::/53", "0:0:0:1000::/52", "0:0:0:2000::/51", "0:0:0:4000::/50", "0:0:0:8000::/49",
            "0:0:1::/48", "0:0:2::/47", "0:0:4::/46", "0:0:8::/45", "0:0:10::/44", "0:0:20::/43", "0:0:40::/42", "0:0:80::/41",
            "0:0:100::/40", "0:0:200::/39", "0:0:400::/38", "0:0:800::/37", "0:0:1000::/36", "0:0:2000::/35", "0:0:4000::/34",
            "0:0:8000::/33", "0:1::/32", "0:2::/31", "0:4::/30", "0:8::/29", "0:10::/28", "0:20::/27", "0:40::/26", "0:80::/25",
            "0:100::/24", "0:200::/23", "0:400::/22", "0:800::/21", "0:1000::/20", "0:2000::/19", "0:4000::/18", "0:8000::/17",
            "1::/16", "2::/15", "4::/14", "8::/13", "10::/12", "20::/11", "40::/10", "80::/9", "100::/8", "200::/7", "400::/6",
            "800::/5", "1000::/4", "2000::/3", "4000::/2", "8000::/2", "c000::/3", "e000::/4", "f000::/5", "f800::/6", "fe00::/9", "fec0::/10"
        )
    }
}
