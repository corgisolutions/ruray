package io.github.corgisolutions.ruray.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import io.github.corgisolutions.ruray.MainActivity
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.data.db.AppDatabase
import io.github.corgisolutions.ruray.data.db.SplitTunnelApp
import kotlinx.coroutines.*

object GovAppScanner {
    private const val TAG = "GOV_SCANNER"
    private const val CHANNEL_ID = "RURAY_GOV_CHANNEL"
    private const val NOTIFICATION_ID = 2
    private const val SCAN_INTERVAL_MS = 60_000L

    val GOVERNMENT_PACKAGES = setOf(
        "ru.oneme.app",
        "com.vkontakte.android",
        "ru.ok.android",
        "ru.ok.live",
        "com.icq.mobile.client",
        "im.tamtam",
        "ru.mail.mailapp",
        "ru.mail.cloud",
        "ru.mail.calendar",

        "ru.vk.store",
        "ru.nashstore.app",

        "ru.sberbankmobile",
        "ru.sberbank.sberapps",
        "ru.sberbank.spasibo",
        "ru.sberbank.sberbankid",
        "ru.sberbankins.insapp",
        "ru.vtb24.mobilebanking.android",
        "ru.gazprombank.android.mobilebank.app",
        "ru.gazprompay.android",
        "ru.rosbank.android",
        "ru.rosbank.pro.android",
        "com.idamob.tinkoff.android",
        "ru.tinkoff.mvno",
        "ru.tinkoff.investing",
        "ru.alfabank.mobile.android",
        "ru.alfadirect.app",
        "com.yandex.bank",
        "ru.raiffeisennews",
        "ru.rocketbank.r2d2",
        "com.hais.bank.netochka",
        "modulbank.ru.app",
        "ru.ozon.fintech.finance",
        "com.bspb.android",
        "ru.mtsbank.mtsbankmobile",
        "com.openbank",
        "ru.sovcomcard.halva.v1",
        "ru.psbank.online",
        "ru.homecredit.mycredit",
        "com.akbars.android.mobilebank",
        "ru.uralsib.umobile.android",

        "ru.rostel",
        "ru.gosuslugi.goskey",
        "ru.gosuslugi.pos",
        "ru.rtlabs.mobile.ebs.gosuslugi.android",
        "ru.gosuslugi.migrant",
        "ru.fns.lkfl",
        "ru.fns.lkip",
        "ru.pfrf.mobile",
        "ru.mvd.gosuslugi.gibdd",
        "ru.fss.lkp",
        "ru.rpn.lichnykabinet",

        "com.mos.polls",
        "ru.mos.app",
        "ru.mos.transport",
        "ru.mos.parking.new",
        "ru.mos.social",
        "ru.mos.guides",
        "ru.mos.edu.diary",

        "ru.nspk.mirpay",
        "ru.nspk.sbp",

        "ru.yandex.taxi",
        "ru.foodfox.client",
        "ru.beru.android",
        "com.yandex.browser",
        "ru.yandex.searchplugin",
        "com.yandex.music",
        "ru.yandex.disk",
        "com.yandex.mail",
        "ru.yandex.yandexmaps",
        "com.yandex.translate",
        "ru.yandex.metro",
        "com.yandex.launcher",
        "ru.yandex.weatherplugin",
        "com.yandex.tv.alice",

        "com.avito.android",
        "com.wildberries.ru",
        "ru.ozon.app.android",
        "ru.aliexpress.buyer",
        "com.logistic.sdek",
        "ru.beru.android",
        "ru.leroymerlin.ecomm",
        "com.mvideo",
        "ru.citilink.citimobile",
        "com.dns.shop",
        "ru.lamoda.android",

        "ru.mts.mymts",
        "ru.megafon.mlk",
        "ru.beeline.services",
        "ru.tele2.mytele2",
        "ru.rt.mobile.promo",
        "ru.yota.android",

        "ru.kfc.kfc_delivery",
        "com.samokat.app",
        "ru.delivery.club.android",
        "ru.magnit.app.retailer",
        "ru.perekrestok.app",
        "com.vkusvill.shop",
        "ru.pyaterochka.app",
        "ru.lenta.android",
        "ru.auchan.retail.android",
        "ru.fix.price",
        "ru.sportmaster.app.android",

        "ru.rzd.passenger",
        "com.aeroflot.app",
        "ru.s7.android",
        "ru.pobeda.app",
        "ru.utair.app",
        "com.mosgorpass.android",
        "ru.mos.metro",

        "com.octopod.russianpost.client.android",
        "ru.taxcom.lkfl",

        "com.infolink.limeiptv",
        "ru.ivi.client",
        "ru.more.play",
        "com.start",
        "ru.kinopoisk.app",
        "ru.premier.app",
        "tv.okko.app",
        "ru.wink.mobile",
        "ru.rutube.app",
        "ru.vgtrk.player",
        "ru.ntv.app",

        "com.ncloudtech.cloudoffice",
        "com.kms.free",
        "ru.naumen.tam",

        "ru.litres.android",
        "com.dnevnik.app",
        "ru.egisz.gosuslugi",
        "ru.drom.pdd.android.app",
        "ru.hh.android",
        "ru.sravniru"
    )

    data class SystemGovApp(
        val packageName: String,
        val label: String
    )

    private var scanJob: Job? = null

    fun startPeriodicScan(context: Context, db: AppDatabase, scope: CoroutineScope) {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            scanAndWhitelist(context, db)
            while (isActive) {
                delay(SCAN_INTERVAL_MS)
                try {
                    scanAndWhitelist(context, db)
                } catch (e: Exception) {
                    Log.e(TAG, "periodic scan", e)
                }
            }
        }
    }

    fun stopPeriodicScan() {
        scanJob?.cancel()
        scanJob = null
    }

    suspend fun scanAndWhitelist(context: Context, db: AppDatabase): List<String> {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)

        val splitMode = prefs.getInt("split_mode", 0)
        if (splitMode != 0) {
            Log.d(TAG, "skipping scan: mode=$splitMode")
            return emptyList()
        }

        val dismissed = prefs.getStringSet("dismissed_gov_apps", emptySet()) ?: emptySet()

        val pm = context.packageManager
        val installed = try {
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
                .mapNotNull { it.packageName }
                .toSet()
        } catch (e: Exception) {
            Log.e(TAG, "failed to get packages", e)
            return emptyList()
        }

        val currentBypass = db.splitTunnelDao().getAllList().toSet()

        val newApps = mutableListOf<String>()

        for (pkg in GOVERNMENT_PACKAGES) {
            if (pkg !in installed) continue
            if (pkg in currentBypass) continue
            if (pkg in dismissed) continue

            val isSystem = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) { false }

            if (isSystem) continue

            try {
                db.splitTunnelDao().insert(SplitTunnelApp(pkg, isAutoAdded = true))
                newApps.add(pkg)
                Log.d(TAG, "auto-whitelisted: $pkg")
            } catch (e: Exception) {
                Log.e(TAG, "failed to insert $pkg", e)
            }
        }

        if (newApps.isNotEmpty()) {
            val existing = prefs.getStringSet("pending_gov_warning", emptySet()) ?: emptySet()
            prefs.edit {
                putStringSet("pending_gov_warning", existing + newApps)
            }
            showNotification(context, newApps)
        }

        prefs.edit { putBoolean("first_scan_complete", true) }

        return newApps
    }

    fun getSystemGovApps(context: Context): List<SystemGovApp> {
        val pm = context.packageManager
        val result = mutableListOf<SystemGovApp>()

        for (pkg in GOVERNMENT_PACKAGES) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (isSystem) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    result.add(SystemGovApp(pkg, label))
                }
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        return result
    }

    fun getPendingSystemGovApps(context: Context): List<SystemGovApp> {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        val dismissed = prefs.getStringSet("dismissed_system_gov_apps", emptySet()) ?: emptySet()
        return getSystemGovApps(context).filterNot { it.packageName in dismissed }
    }

    fun dismissSystemGovWarning(context: Context) {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        val current = getSystemGovApps(context).map { it.packageName }.toSet()
        prefs.edit {
            putStringSet("dismissed_system_gov_apps", current)
        }
    }

    fun openAppSettings(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "failed to open settings for $packageName", e)
        }
    }

    fun getPendingWarningApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        return prefs.getStringSet("pending_gov_warning", emptySet()) ?: emptySet()
    }

    fun isFirstScanComplete(context: Context): Boolean {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("first_scan_complete", false)
    }

    fun hasShownGovDialog(context: Context): Boolean {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        return prefs.getBoolean("gov_dialog_shown", false)
    }

    fun markGovDialogShown(context: Context) {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("gov_dialog_shown", true) }
    }

    fun getInstalledGovApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val result = mutableListOf<Pair<String, String>>()

        for (pkg in GOVERNMENT_PACKAGES) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!isSystem) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    result.add(pkg to label)
                }
            } catch (_: PackageManager.NameNotFoundException) { }
        }

        return result
    }

    fun dismissWarning(context: Context) {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        prefs.edit {
            putStringSet("pending_gov_warning", emptySet())
        }
    }

    fun markAsDismissed(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE)
        val dismissed = prefs.getStringSet("dismissed_gov_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        dismissed.add(packageName)

        val pending = prefs.getStringSet("pending_gov_warning", emptySet())?.toMutableSet() ?: mutableSetOf()
        pending.remove(packageName)

        prefs.edit {
            putStringSet("dismissed_gov_apps", dismissed)
            putStringSet("pending_gov_warning", pending)
        }
        Log.d(TAG, "dismissed: $packageName")
    }

    private fun showNotification(context: Context, apps: List<String>) {
        createNotificationChannel(context)

        val localizedContext = LocaleHelper.onAttach(context)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val appNames = apps.mapNotNull { pkg ->
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) { null }
        }

        val title = localizedContext.getString(R.string.gov_apps_whitelisted_title)
        val text = if (appNames.size == 1) {
            localizedContext.getString(R.string.gov_apps_whitelisted_single, appNames.first())
        } else {
            localizedContext.getString(R.string.gov_apps_whitelisted_multiple, apps.size)
        }

        val bigText = localizedContext.getString(R.string.gov_apps_whitelisted_detail, appNames.joinToString(", "))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        val localizedContext = LocaleHelper.onAttach(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.gov_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = localizedContext.getString(R.string.gov_channel_desc)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }
}