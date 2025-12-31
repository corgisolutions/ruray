package io.github.corgisolutions.ruray.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import io.github.corgisolutions.ruray.Config
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.data.db.AppDatabase
import io.github.corgisolutions.ruray.data.db.VlessHost
import io.github.corgisolutions.ruray.network.VlessManager
import io.github.corgisolutions.ruray.services.ProxyService
import io.github.corgisolutions.ruray.ui.components.*
import io.github.corgisolutions.ruray.ui.viewmodels.MainViewModel
import io.github.corgisolutions.ruray.update.DownloadState
import io.github.corgisolutions.ruray.update.UpdateChecker
import io.github.corgisolutions.ruray.update.UpdateDownloader
import io.github.corgisolutions.ruray.update.UpdateInfo
import io.github.corgisolutions.ruray.update.UpdateInstaller
import io.github.corgisolutions.ruray.update.UpdateNotificationManager
import io.github.corgisolutions.ruray.utils.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(launchId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val viewModel: MainViewModel = viewModel()
    val allHosts by viewModel.hosts.collectAsStateWithLifecycle()

    val db = remember { AppDatabase.getDatabase(context) }
    val vlessManager = remember { VlessManager(context) }

    var testingLink by remember { mutableStateOf<String?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    var status by remember { mutableStateOf(context.getString(R.string.status_idle)) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("proxy_state", Context.MODE_PRIVATE) }
    var activeLink by remember { mutableStateOf(prefs.getString("active_link", null)) }

    var lastActiveLink by remember { mutableStateOf(activeLink) }
    if (activeLink != null) lastActiveLink = activeLink

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "active_link") {
                activeLink = prefs.getString("active_link", null)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val isRestarting = remember { activity?.intent?.getBooleanExtra("IS_RESTARTING", false) == true }
    val isLanguageTransition = remember { activity?.intent?.getBooleanExtra("LANGUAGE_TRANSITION", false) == true }

    var isBackgroundScanEnabled by remember { mutableStateOf(prefs.getBoolean("bg_scan", true)) }
    var isVpnModeEnabled by remember { mutableStateOf(prefs.getBoolean("vpn_mode", true)) }
    var isSplitTunnelingEnabled by remember { mutableStateOf(prefs.getBoolean("split_tunnel", false)) }

    var isLanSharingEnabled by remember { mutableStateOf(prefs.getBoolean("lan_sharing", false)) }
    var appliedLanSharingEnabled by remember { mutableStateOf(isLanSharingEnabled) }

    var isKillswitchEnabled by remember { mutableStateOf(prefs.getBoolean("killswitch", true)) }
    var splitTunnelMode by remember { mutableIntStateOf(prefs.getInt("split_mode", 0)) }
    var isAdvancedNetworkExpanded by remember {
        mutableStateOf(
            if (isLanguageTransition) prefs.getBoolean("advanced_expanded", false) else false
        )
    }
    LaunchedEffect(isAdvancedNetworkExpanded) {
        prefs.edit { putBoolean("advanced_expanded", isAdvancedNetworkExpanded) }
    }
    var customMtu by remember { mutableStateOf(prefs.getString("custom_mtu", "") ?: "") }
    var dnsPreset by remember { mutableIntStateOf(prefs.getInt("dns_preset", 0)) }
    var customDns by remember { mutableStateOf(prefs.getString("custom_dns", "") ?: "") }
    var customAddresses by remember { mutableStateOf(prefs.getString("custom_addresses", "") ?: "") }

    var socksPort by remember { mutableStateOf(prefs.getString("socks_port", "55555") ?: "55555") }
    var appliedSocksPort by remember { mutableStateOf(socksPort) }

    val safeSocksPort = remember(socksPort) {
        socksPort.toIntOrNull()?.takeIf { it in 1024..65535 } ?: 55555
    }

    var isSocks5Expanded by remember {
        mutableStateOf(
            if (isLanguageTransition) prefs.getBoolean("socks5_expanded", false) else false
        )
    }
    LaunchedEffect(isSocks5Expanded) {
        prefs.edit { putBoolean("socks5_expanded", isSocks5Expanded) }
    }

    var showAppManager by rememberSaveable(launchId) { mutableStateOf(false) }
    var pendingGovApps by remember { mutableStateOf(GovAppScanner.getPendingWarningApps(context)) }
    var pendingSystemGovApps by remember { mutableStateOf(GovAppScanner.getPendingSystemGovApps(context)) }
    var showSystemGovWarning by remember { mutableStateOf(pendingSystemGovApps.isNotEmpty()) }
    var showGovAppDialog by remember { mutableStateOf(false) }
    var govAppsForDialog by remember { mutableStateOf<List<GovAppDisplayInfo>>(emptyList()) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }

    val showUpdateFromIntent = remember {
        activity?.intent?.getBooleanExtra("show_update_dialog", false) == true
    }

    LaunchedEffect(showUpdateFromIntent, updateInfo) {
        if (showUpdateFromIntent && updateInfo != null) {
            showUpdateDialog = true
            activity?.intent?.removeExtra("show_update_dialog")
        }
    }

    LaunchedEffect(activeLink) {
        if (activeLink != null) {
            delay(3000)
            val info = UpdateChecker.checkForUpdate(context, safeSocksPort)
            if (info != null) {
                updateInfo = info
                UpdateNotificationManager.showUpdateNotification(context, info.versionName)
            }
        }
    }

    LaunchedEffect(activeLink) {
        if (activeLink != null) {
            while (true) {
                delay(30 * 60 * 1000L)
                val info = UpdateChecker.checkForUpdate(context, safeSocksPort)
                if (info != null && (updateInfo == null || info.versionCode > updateInfo!!.versionCode)) {
                    updateInfo = info
                    UpdateNotificationManager.showUpdateNotification(context, info.versionName)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            pendingGovApps = GovAppScanner.getPendingWarningApps(context)

            val systemApps = GovAppScanner.getPendingSystemGovApps(context)
            if (systemApps.isNotEmpty() && !showSystemGovWarning && pendingSystemGovApps.isEmpty()) {
                pendingSystemGovApps = systemApps
                showSystemGovWarning = true
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!GovAppScanner.hasShownGovDialog(context)) {
            val installed = GovAppScanner.getInstalledGovApps(context)
            if (installed.isNotEmpty()) {
                govAppsForDialog = installed.map { GovAppDisplayInfo(it.first, it.second) }
                showGovAppDialog = true
            }
        }
    }

    val settingsScrollState = rememberScrollState(
        initial = if (isLanguageTransition) prefs.getInt("settings_scroll", 0) else 0
    )

    LaunchedEffect(settingsScrollState.value) {
        prefs.edit { putInt("settings_scroll", settingsScrollState.value) }
    }

    val cachedServerCount = remember { activity?.intent?.getIntExtra("CACHED_COUNT", 0) ?: 0 }
    val cachedAppCount = remember { activity?.intent?.getIntExtra("CACHED_APP_COUNT", 0) ?: 0 }

    val splitTunnelAppCount by produceState(initialValue = cachedAppCount) {
        db.splitTunnelDao().getAll().collect { value = it.size }
    }

    fun triggerVpnReload() {
        val intent = Intent(context, ProxyService::class.java)
        intent.action = ProxyService.ACTION_RELOAD_TUN
        context.startService(intent)
    }

    fun restartVpnCore() {
        if (activeLink != null) {
            val intent = Intent(context, ProxyService::class.java)
            intent.action = "START"
            intent.putExtra("LINK", activeLink)
            intent.putExtra("SOCKS_PORT", safeSocksPort)
            context.startForegroundService(intent)

            appliedSocksPort = socksPort
            appliedLanSharingEnabled = isLanSharingEnabled

            status = context.getString(R.string.status_restarting_core)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val stored = prefs.getString("active_link", null)
                activeLink = stored
                if (stored != null) status = context.getString(R.string.status_connected)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeLink, scanJob, isRefreshing) {
        if (activeLink != null) {
            if (status == context.getString(R.string.status_connecting) || status == context.getString(R.string.status_idle)) {
                status = context.getString(R.string.status_connected)
            }
        } else if (scanJob == null && !isRefreshing && status != context.getString(R.string.status_scanning) && status != context.getString(R.string.status_restarting_core)) {
            status = context.getString(R.string.status_idle)
        }
    }

    val sortedHosts = remember(allHosts, activeLink, lastActiveLink) {
         allHosts
            .distinctBy {
                parseVlessLink(it.link)?.let { d -> "${d.address}:${d.port}" } ?: it.link
            }
            .sortedWith(compareByDescending<VlessHost> {
                areLinksEquivalent(it.link, activeLink) || areLinksEquivalent(it.link, lastActiveLink)
            }
            .thenByDescending { it.latency > 0 }
            .thenBy { it.latency })
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeLink) {
        if (activeLink != null) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(isBackgroundScanEnabled, activeLink) {
        if (scanJob?.isActive == true && status == context.getString(R.string.status_scanning)) return@LaunchedEffect

        scanJob?.cancel()
        scanJob = null

        if (isBackgroundScanEnabled && activeLink != null) {
            scanJob = launch {
                val semaphore = Semaphore(Config.BACKGROUND_SCAN_CONCURRENCY)
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

                while (isActive) {
                    delay(Config.BACKGROUND_SCAN_INTERVAL_MS)

                    val isSafeToScan = try {
                        @Suppress("DEPRECATION") // fuck you, we need the true interface
                        val underlyingNetwork = cm.allNetworks.firstOrNull { network ->
                            val c = cm.getNetworkCapabilities(network)
                            c != null &&
                            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        }
                        underlyingNetwork != null &&
                        cm.getNetworkCapabilities(underlyingNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    } catch (_: Exception) {
                        false
                    }

                    if (!isSafeToScan) continue

                    val all = db.vlessDao().getAllHostsList()
                    if (all.isEmpty()) continue

                    all.map { host ->
                        if (areLinksEquivalent(host.link, activeLink)) return@map null
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val latency = vlessManager.testHost(host.link, Config.LATENCY_TIMEOUT_MS)
                                if (latency > 0) {
                                    db.vlessDao().updateSuccess(host.link, true, latency, System.currentTimeMillis())
                                    if (host.countryCode == null) {
                                        launch { fetchAndUpdateCountry(host.link, db) }
                                    }
                                } else {
                                    db.vlessDao().updateFailure(host.link, System.currentTimeMillis())
                                }
                            }
                        }
                    }.filterNotNull().awaitAll()
                    db.vlessDao().deleteFailed(Config.HOST_FAIL_DELETE_THRESHOLD)
                }
            }
        }
    }

    var pendingVpnLink by remember { mutableStateOf<String?>(null) }

    fun saveSettingsAndStart(link: String) {
        val bind = if (isLanSharingEnabled) "0.0.0.0" else "127.0.0.1"
        prefs.edit {
            putString("socks_port", socksPort)
            putBoolean("lan_sharing", isLanSharingEnabled)
            putString("bind_address", bind)
            putString("active_link", link)
        }

        val serviceIntent = Intent(context, ProxyService::class.java)
        serviceIntent.action = "START"
        serviceIntent.putExtra("LINK", link)
        serviceIntent.putExtra("SOCKS_PORT", safeSocksPort)
        context.startForegroundService(serviceIntent)

        activeLink = link
        status = context.getString(R.string.status_connecting)

        appliedSocksPort = socksPort
        appliedLanSharingEnabled = isLanSharingEnabled
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingVpnLink?.let { link ->
            if (result.resultCode == Activity.RESULT_OK) {
                saveSettingsAndStart(link)
            }
        }
        //pendingVpnLink = null
    }

    fun startVpnService(link: String) {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            pendingVpnLink = link
            vpnLauncher.launch(intent)
        } else {
            saveSettingsAndStart(link)
        }
    }

    fun stopVpnService() {
        val intent = Intent(context, ProxyService::class.java)
        intent.action = "STOP"
        context.startService(intent)

        activeLink = null
        prefs.edit { putString("active_link", null) }
        status = context.getString(R.string.status_stopped)
        progress = 0f
    }

    if (showAppManager) {
        AppManagerDialog(
            db = db,
            onDismiss = {
                showAppManager = false
                triggerVpnReload()
            }
        )
    }

    val density = LocalDensity.current

    if (showSystemGovWarning && pendingSystemGovApps.isNotEmpty()) {
        SystemGovWarningDialog(
            apps = pendingSystemGovApps,
            onDismiss = {
                GovAppScanner.dismissSystemGovWarning(context)
                showSystemGovWarning = false
                pendingSystemGovApps = emptyList()
            }
        )
    }
    else {
        if (showGovAppDialog && govAppsForDialog.isNotEmpty()) {
            GovAppWarningDialog(
                apps = govAppsForDialog,
                onManageApps = {
                    showAppManager = true
                },
                onDismiss = {
                    GovAppScanner.markGovDialogShown(context)
                    showGovAppDialog = false
                }
            )
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            downloadProgress = downloadProgress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            isDownloading = isDownloading,
            onConfirm = {
                if (downloadedApkFile != null) {
                    UpdateInstaller.installApk(context, downloadedApkFile!!)
                } else {
                    scope.launch {
                        isDownloading = true
                        UpdateDownloader.downloadApk(context, updateInfo!!.downloadUrl, safeSocksPort)
                            .collect { state ->
                                when (state) {
                                    is DownloadState.Progress -> {
                                        downloadedBytes = state.bytesDownloaded
                                        totalBytes = state.totalBytes
                                        downloadProgress = if (state.totalBytes > 0) {
                                            state.bytesDownloaded.toFloat() / state.totalBytes
                                        } else 0f
                                    }
                                    is DownloadState.Success -> {
                                        isDownloading = false
                                        downloadedApkFile = state.file
                                        UpdateNotificationManager.cancelNotification(context)

                                        val stopIntent = Intent(context, ProxyService::class.java)
                                        stopIntent.action = "STOP"
                                        context.startService(stopIntent)

                                        UpdateInstaller.installApk(context, state.file)
                                    }
                                    is DownloadState.Error -> {
                                        isDownloading = false
                                        downloadProgress = null
                                    }
                                }
                            }
                    }
                }
            },
            onDismiss = {
                if (!isDownloading) {
                    showUpdateDialog = false
                }
            }
        )
    }

    val initialSheetExpanded = remember {
        val isLangTransition = activity?.intent?.getBooleanExtra("LANGUAGE_TRANSITION", false) == true
        if (isLangTransition) prefs.getBoolean("sheet_expanded", false) else false
    }
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = if (initialSheetExpanded) SheetValue.Expanded else SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
    )

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        prefs.edit { putBoolean("sheet_expanded", scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) }
    }

    var isContentVisible by remember { mutableStateOf(!isRestarting) }
    var isTransitioning by remember { mutableStateOf(isLanguageTransition) }
    var hasStartedFadeOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isRestarting) {
            delay(50)
            isContentVisible = true
        }
    }

    LaunchedEffect(isLanguageTransition) {
        if (isLanguageTransition && !hasStartedFadeOut) {
            delay(50)
            isTransitioning = false
            hasStartedFadeOut = true
        }
        if (!isLanguageTransition) {
            hasStartedFadeOut = false
        }
    }

    val blurRadius by animateDpAsState(
        targetValue = if (isContentVisible && !isTransitioning) 0.dp else 12.dp,
        animationSpec = if (isLanguageTransition && !hasStartedFadeOut) tween(0) else tween(400),
        label = "blur"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isContentVisible && !isTransitioning) 1f else 0.4f,
        animationSpec = if (isLanguageTransition && !hasStartedFadeOut) tween(0) else tween(400),
        label = "alpha"
    )

    BackHandler {
        if (isTransitioning) return@BackHandler

        if (showAppManager) {
            @Suppress("AssignedValueIsNeverRead") // ???
            showAppManager = false
            triggerVpnReload()
        } else if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
        } else {
            if (activeLink != null) {
                activity?.moveTaskToBack(true)
            } else {
                activity?.finish()
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val layoutHeightPx = constraints.maxHeight.toFloat()
        val peekHeightPx = with(density) { 126.dp.toPx() }
        val collapsedOffset = layoutHeightPx - peekHeightPx

        var cachedProgress by remember { mutableFloatStateOf(if (initialSheetExpanded) 1f else 0f) }

        val rawProgress = remember {
            derivedStateOf {
                try {
                    val offset = scaffoldState.bottomSheetState.requireOffset()
                    if (offset.isNaN()) {
                         if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) 1f else 0f
                    } else {
                        ((collapsedOffset - offset) / collapsedOffset).coerceIn(0f, 1f)
                    }
                } catch (_: Exception) {
                    if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) 1f else 0f
                }
            }
        }.value

        if (!isTransitioning) {
            cachedProgress = rawProgress
        }

        val sheetProgress = if (isTransitioning) cachedProgress else rawProgress

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 126.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surface,
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            sheetTonalElevation = 8.dp,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetDragHandle = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isTransitioning) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                                        scaffoldState.bottomSheetState.partialExpand()
                                    } else {
                                        scaffoldState.bottomSheetState.expand()
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(100))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }
            },
            sheetContent = {
                CompositionLocalProvider(
                    LocalContentColor provides LocalContentColor.current.copy(alpha = contentAlpha)
                ) {
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {

                        val isConnected = activeLink != null || scanJob != null

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { clip = false }
                                .blur(blurRadius)
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .animateContentSize(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (isTransitioning) return@Button
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isConnected) {
                                                scanJob?.cancel()
                                                scanJob = null
                                                stopVpnService()
                                            } else {
                                                scanJob = scope.launch {
                                                    val semaphore = Semaphore(Config.SCAN_CONCURRENCY)
                                                    status = context.getString(R.string.status_scanning)
                                                    val all = db.vlessDao().getAllHostsList()
                                                    if (all.isEmpty()) {
                                                        status = context.getString(R.string.status_no_hosts)
                                                        scanJob = null
                                                        return@launch
                                                    }

                                                    val checked = java.util.concurrent.atomic.AtomicInteger(0)
                                                    val total = all.size
                                                    val progressInterval = (total / 20).coerceAtLeast(1)

                                                    all.map { host ->
                                                        async(Dispatchers.IO) {
                                                            semaphore.withPermit {
                                                                val latency = vlessManager.testHost(host.link, Config.LATENCY_TIMEOUT_MS)
                                                                if (latency > 0) {
                                                                    db.vlessDao().updateSuccess(host.link, true, latency, System.currentTimeMillis())
                                                                    if (host.countryCode == null) {
                                                                        launch { fetchAndUpdateCountry(host.link, db) }
                                                                    }
                                                                } else {
                                                                    db.vlessDao().updateFailure(host.link, System.currentTimeMillis())
                                                                }
                                                                checked.incrementAndGet()
                                                                if (checked.get() % progressInterval == 0 || checked.get() == total) {
                                                                    withContext(Dispatchers.Main) {
                                                                        progress = checked.get().toFloat() / total
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }.awaitAll()
                                                    db.vlessDao().deleteFailed(Config.HOST_FAIL_DELETE_THRESHOLD)
                                                    val best = db.vlessDao().getAllHostsList().firstOrNull()
                                                    if (best != null && best.latency > 0) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        startVpnService(best.link)
                                                    } else {
                                                        status = context.getString(R.string.status_no_working_hosts)
                                                        scanJob = null
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(if (isConnected) Icons.Rounded.Stop else Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isConnected) stringResource(R.string.stop) else stringResource(R.string.connect))
                                    }

                                    if (!isConnected) {
                                        Button(
                                            onClick = {
                                                if (isTransitioning) return@Button
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                scanJob = scope.launch {
                                                    status = context.getString(R.string.status_quick)
                                                    val candidates = db.vlessDao().getAllHostsList()
                                                        .filter { it.latency > 0 }
                                                        .sortedBy { it.latency }

                                                    if (candidates.isEmpty()) {
                                                        status = context.getString(R.string.status_no_history)
                                                        delay(1000)
                                                        status = context.getString(R.string.status_idle)
                                                        scanJob = null
                                                        return@launch
                                                    }

                                                    for (host in candidates) {
                                                        if (!isActive) break
                                                        status = context.getString(R.string.status_quick_checking, getHostDisplay(host.link))

                                                        val latency = vlessManager.testHost(host.link, Config.LATENCY_TIMEOUT_MS)
                                                        if (latency > 0) {
                                                            db.vlessDao().updateSuccess(host.link, true, latency, System.currentTimeMillis())
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            startVpnService(host.link)
                                                            return@launch
                                                        } else {
                                                            db.vlessDao().updateFailure(host.link, System.currentTimeMillis())
                                                        }
                                                    }
                                                    status = context.getString(R.string.status_quick_connect_failed)
                                                    delay(1000)
                                                    status = context.getString(R.string.status_idle)
                                                    scanJob = null
                                                }
                                            },
                                            modifier = Modifier.height(50.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) {
                                            Box(modifier = Modifier.graphicsLayer { rotationZ = -10f }) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.FlashOn,
                                                        contentDescription = "Quick Connect",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Rounded.FlashOn,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        val headerAlpha = ((sheetProgress - 0.1f) / 0.4f).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 24.dp, start = 8.dp)
                                .graphicsLayer { 
                                    clip = false 
                                    alpha = headerAlpha
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier
                                .graphicsLayer { clip = false }
                                .blur(blurRadius)
                                .alpha(contentAlpha)) {
                                Text(
                                    stringResource(R.string.settings),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            val currentLang = LocaleHelper.getLanguage(context)
                            val isRu = currentLang == "ru"

                            var switchState by remember { mutableStateOf(isRu) }
                            LaunchedEffect(isRu) { switchState = isRu }

                            val bias by animateFloatAsState(
                                targetValue = if (switchState) 1f else -1f,
                                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                                label = "lang_switch"
                            )

                            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                                Box(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(100))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (isTransitioning) return@clickable
                                            if (scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded) return@clickable

                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isTransitioning = true
                                            switchState = !switchState

                                            prefs.edit { putBoolean("sheet_expanded", true) }

                                            scope.launch {
                                                delay(400)
                                                val newLang = if (isRu) "en" else "ru"
                                                LocaleHelper.setLocale(context, newLang)

                                                val intent = Intent(context, activity!!::class.java)
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                                intent.putExtra("CACHED_COUNT", sortedHosts.size)
                                                intent.putExtra("CACHED_APP_COUNT", splitTunnelAppCount)
                                                intent.putExtra("LANGUAGE_TRANSITION", true)
                                                intent.putExtra("LAUNCH_ID", launchId)
                                                
                                                activity.startActivity(intent)

                                                @Suppress("DEPRECATION")
                                                activity.overridePendingTransition(0, 0)

                                                activity.finish()

                                                @Suppress("DEPRECATION")
                                                activity.overridePendingTransition(0, 0)
                                            }
                                        }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(36.dp)
                                            .align(BiasAlignment(bias, 0f))
                                            .shadow(2.dp, RoundedCornerShape(100))
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(100))
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                            Text("🇬🇧", fontSize = 18.sp, modifier = Modifier.alpha(if (!switchState) 1f else 0.5f))
                                        }
                                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                            Text("🇷🇺", fontSize = 18.sp, modifier = Modifier.alpha(if (switchState) 1f else 0.5f))
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .blur(blurRadius)
                            .alpha(contentAlpha)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(settingsScrollState)
                                    .padding(vertical = 12.dp, horizontal = 24.dp)
                            ) {
                                CapabilitiesSheet(
                                    isBackgroundScanEnabled = isBackgroundScanEnabled,
                                    onBackgroundScanChange = {
                                        isBackgroundScanEnabled = it
                                        prefs.edit { putBoolean("bg_scan", it) }
                                    },
                                    isVpnModeEnabled = isVpnModeEnabled,
                                    onVpnModeChange = {
                                        isVpnModeEnabled = it
                                        prefs.edit { putBoolean("vpn_mode", it) }
                                        triggerVpnReload()
                                    },
                                    isSplitTunnelingEnabled = isSplitTunnelingEnabled,
                                    onSplitTunnelingChange = {
                                        isSplitTunnelingEnabled = it
                                        prefs.edit { putBoolean("split_tunnel", it) }
                                    },
                                    splitTunnelMode = splitTunnelMode,
                                    onSplitTunnelModeChange = {
                                        splitTunnelMode = it
                                        prefs.edit { putInt("split_mode", it) }
                                        triggerVpnReload()
                                    },
                                    splitTunnelAppCount = splitTunnelAppCount,
                                    onManageApps = { showAppManager = true },
                                    isKillswitchEnabled = isKillswitchEnabled,
                                    onKillswitchChange = {
                                        isKillswitchEnabled = it
                                        prefs.edit { putBoolean("killswitch", it) }
                                        triggerVpnReload()
                                    },
                                    isLanSharingEnabled = isLanSharingEnabled,
                                    onLanSharingChange = {
                                        isLanSharingEnabled = it
                                        if (activeLink == null) {
                                            val bind = if (it) "0.0.0.0" else "127.0.0.1"
                                            prefs.edit {
                                                putBoolean("lan_sharing", it)
                                                putString("bind_address", bind)
                                            }
                                            appliedLanSharingEnabled = it
                                        }
                                    },
                                    socksPort = socksPort,
                                    onSocksPortChange = { newValue ->
                                        val filtered = newValue.filter { it.isDigit() }.take(5)
                                        socksPort = filtered
                                        if (activeLink == null) {
                                            prefs.edit { putString("socks_port", filtered) }
                                            appliedSocksPort = filtered
                                        }
                                    },
                                    appliedSocksPort = appliedSocksPort,
                                    appliedLanSharingEnabled = appliedLanSharingEnabled,
                                    activeLink = activeLink,
                                    onRestartCore = {
                                        val bind = if (isLanSharingEnabled) "0.0.0.0" else "127.0.0.1"
                                        prefs.edit {
                                            putString("socks_port", socksPort)
                                            putBoolean("lan_sharing", isLanSharingEnabled)
                                            putString("bind_address", bind)
                                        }
                                        restartVpnCore()
                                    },
                                    isSocks5Expanded = isSocks5Expanded,
                                    onSocks5ExpandedChange = { isSocks5Expanded = it },
                                    isAdvancedNetworkExpanded = isAdvancedNetworkExpanded,
                                    onAdvancedNetworkExpandedChange = { isAdvancedNetworkExpanded = it },
                                    customMtu = customMtu,
                                    onCustomMtuChange = {
                                        customMtu = it
                                        prefs.edit { putString("custom_mtu", it) }
                                    },
                                    dnsPreset = dnsPreset,
                                    onDnsPresetChange = {
                                        dnsPreset = it
                                        prefs.edit { putInt("dns_preset", it) }
                                    },
                                    customDns = customDns,
                                    onCustomDnsChange = {
                                        customDns = it
                                        prefs.edit { putString("custom_dns", it) }
                                    },
                                    customAddresses = customAddresses,
                                    onCustomAddressesChange = {
                                        customAddresses = it
                                        prefs.edit { putString("custom_addresses", it) }
                                    },
                                    updateInfo = updateInfo,
                                    onUpdateClick = { showUpdateDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {

                CompositionLocalProvider(
                    LocalContentColor provides LocalContentColor.current.copy(alpha = contentAlpha)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = 1f - (sheetProgress * 0.05f)
                                scaleX = scale
                                scaleY = scale
                                alpha = 1f - (sheetProgress * 0.3f)
                            }
                            .blur((sheetProgress * 4).dp + blurRadius)
                            .alpha(contentAlpha)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(status, style = MaterialTheme.typography.bodyMedium)

                                    if (activeLink != null) {
                                         val display = getHostDisplay(activeLink!!)
                                         val hostEntry = sortedHosts.find { it.link == activeLink }
                                         val flag = CountryUtils.getFlagEmoji(hostEntry?.countryCode)

                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                             Text(display, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF64B5F6))
                                             if (flag != "🌐") {
                                                 Spacer(Modifier.width(8.dp))
                                                 Text(flag, style = MaterialTheme.typography.bodyLarge)
                                             }
                                         }
                                    } else {
                                        val countToDisplay = if (sortedHosts.isEmpty() && cachedServerCount > 0) cachedServerCount else sortedHosts.size
                                        Text(stringResource(R.string.servers, countToDisplay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }

                                    if (progress > 0f && progress < 1f) {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            strokeCap = StrokeCap.Square,
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = pendingGovApps.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF3D3000)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB800),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.gov_warning_title),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFFB800)
                                        )
                                        Spacer(Modifier.weight(1f))
                                        IconButton(
                                            onClick = {
                                                GovAppScanner.dismissWarning(context)
                                                pendingGovApps = emptySet()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.close),
                                                tint = Color(0xFFFFB800),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = stringResource(R.string.gov_warning_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFFD54F),
                                        lineHeight = 16.sp
                                    )

                                    val appNames = remember(pendingGovApps) {
                                        pendingGovApps.mapNotNull { pkg ->
                                            try {
                                                val pm = context.packageManager
                                                val appInfo = pm.getApplicationInfo(pkg, 0)
                                                pm.getApplicationLabel(appInfo).toString()
                                            } catch (_: Exception) { null }
                                        }.sorted()
                                    }

                                    if (appNames.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = appNames.joinToString(", "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFE082),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = stringResource(R.string.gov_warning_manage),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFB800),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    GovAppScanner.dismissWarning(context)
                                                    pendingGovApps = emptySet()
                                                    showAppManager = true
                                                }
                                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 16.dp))

                        val listAlpha by animateFloatAsState(
                            targetValue = if (sortedHosts.isEmpty() && cachedServerCount > 0) 0f else 1f,
                            animationSpec = tween(400),
                            label = "list_fade"
                        )

                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                scope.launch {
                                    isRefreshing = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    status = context.getString(R.string.status_fetching)
                                    val port = if (activeLink != null) safeSocksPort else 0
                                    val addedCount = HostUpdater.updateHosts(db, proxyPort = port)

                                    status = if (addedCount > 0) {
                                        context.getString(R.string.status_added_hosts, addedCount)
                                    } else {
                                        context.getString(R.string.status_no_new_hosts)
                                    }
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp)
                                .alpha(listAlpha)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
                            ) {
                                items(items = sortedHosts, key = { it.link }) { host ->
                                    val isActive = areLinksEquivalent(host.link, activeLink)
                                    val isTesting = testingLink == host.link

                                    HostItem(
                                        host = host,
                                        isActive = isActive,
                                        isTesting = isTesting,
                                        haptics = haptics,
                                        onClick = {
                                            scope.launch {
                                                testingLink = host.link
                                                val display = getHostDisplay(host.link)
                                                status = context.getString(R.string.status_testing, display)

                                                val latency = vlessManager.testHost(host.link, Config.LATENCY_TIMEOUT_LONG_MS)
                                                if (latency > 0) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    db.vlessDao().updateSuccess(host.link, true, latency, System.currentTimeMillis())
                                                    if (host.countryCode == null) {
                                                        launch { fetchAndUpdateCountry(host.link, db) }
                                                    }
                                                    startVpnService(host.link)
                                                } else {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    delay(100)
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    status = context.getString(R.string.status_connection_failed)
                                                }
                                                testingLink = null
                                            }
                                        }
                                    )

                                }
                            }
                        }
                    }
                }

                if (sheetProgress > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(2f)
                            .background(Color.Black.copy(alpha = sheetProgress * 0.7f))
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                })
                            }
                    )
                }
            }
        }
        
        if (isTransitioning || contentAlpha < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(99f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }
    }
}

private suspend fun fetchAndUpdateCountry(link: String, db: AppDatabase) {
    try {
        val details = parseVlessLink(link) ?: return
        val code = CountryUtils.fetchCountryCode(details.address) ?: return
        db.vlessDao().updateCountry(link, code)
    } catch (_: Exception) {}
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// unused but not sure what it's doing here
/*fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)*/
