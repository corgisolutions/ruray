package io.github.corgisolutions.ruray.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.ui.components.ExpandableInfoRow
import io.github.corgisolutions.ruray.ui.components.SettingRow
import io.github.corgisolutions.ruray.utils.NetworkUtils
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.input.VisualTransformation
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import io.github.corgisolutions.ruray.BuildConfig
import io.github.corgisolutions.ruray.update.UpdateInfo

@Composable
fun CapabilitiesSheet(
    isBackgroundScanEnabled: Boolean,
    onBackgroundScanChange: (Boolean) -> Unit,
    isVpnModeEnabled: Boolean,
    onVpnModeChange: (Boolean) -> Unit,
    isSplitTunnelingEnabled: Boolean,
    onSplitTunnelingChange: (Boolean) -> Unit,
    splitTunnelMode: Int,
    onSplitTunnelModeChange: (Int) -> Unit,
    splitTunnelAppCount: Int,
    onManageApps: () -> Unit,
    isKillswitchEnabled: Boolean,
    onKillswitchChange: (Boolean) -> Unit,
    isLanSharingEnabled: Boolean,
    onLanSharingChange: (Boolean) -> Unit,
    socksPort: String,
    onSocksPortChange: (String) -> Unit,
    appliedSocksPort: String,
    appliedLanSharingEnabled: Boolean,
    activeLink: String?,
    onRestartCore: () -> Unit,
    isSocks5Expanded: Boolean,
    onSocks5ExpandedChange: (Boolean) -> Unit,
    isAdvancedNetworkExpanded: Boolean,
    onAdvancedNetworkExpandedChange: (Boolean) -> Unit,
    customMtu: String,
    onCustomMtuChange: (String) -> Unit,
    dnsPreset: Int,
    onDnsPresetChange: (Int) -> Unit,
    customDns: String,
    onCustomDnsChange: (String) -> Unit,
    customAddresses: String,
    onCustomAddressesChange: (String) -> Unit,
    updateInfo: UpdateInfo? = null,
    onUpdateClick: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingRow(
            icon = Icons.Rounded.Radar,
            label = stringResource(R.string.background_scan),
            description = stringResource(R.string.passive_health_checks),
            checked = isBackgroundScanEnabled,
            onCheckedChange = {
                onBackgroundScanChange(it)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )

        SettingRow(
            icon = Icons.Rounded.VpnKey,
            label = stringResource(R.string.vpn_mode),
            description = stringResource(R.string.tunnel_all_device_traffic),
            checked = isVpnModeEnabled,
            onCheckedChange = {
                onVpnModeChange(it)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )

        val isSplitTunnelDisabled = !isVpnModeEnabled

        ExpandableInfoRow(
            icon = Icons.AutoMirrored.Rounded.CallSplit,
            label = stringResource(R.string.split_tunneling),
            description = if (isSplitTunnelDisabled) stringResource(R.string.split_tunnel_unavailable)
            else when (splitTunnelMode) {
                0 -> stringResource(R.string.split_tunnel_bypass)
                2 -> stringResource(R.string.split_tunnel_only_selected)
                else -> stringResource(R.string.split_tunnel_all_apps)
            },
            expanded = isSplitTunnelingEnabled && !isSplitTunnelDisabled,
            enabled = !isSplitTunnelDisabled,
            onExpandChange = {
                if (!isSplitTunnelDisabled) {
                    onSplitTunnelingChange(it)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        ) {
            Column(modifier = Modifier.padding(start = 56.dp, top = 4.dp, bottom = 8.dp, end = 4.dp)) {
                val items = listOf(
                    Triple(0, stringResource(R.string.mode_bypass), Icons.Rounded.Remove),
                    Triple(1, stringResource(R.string.mode_all), Icons.Rounded.AllInclusive),
                    Triple(2, stringResource(R.string.mode_isolate), Icons.Rounded.Add)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    val segmentWidth = maxWidth / 3
                    val indicatorOffset by animateDpAsState(
                        targetValue = segmentWidth * splitTunnelMode,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "indicator"
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        items.forEach { (index, label, icon) ->
                            val isSelected = splitTunnelMode == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onSplitTunnelModeChange(index)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = splitTunnelMode != 1,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onManageApps()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.manage_apps_count, splitTunnelAppCount), fontSize = 12.sp)
                    }
                }
            }
        }

        SettingRow(
            icon = Icons.Rounded.Block,
            label = stringResource(R.string.block_local_network),
            description = if (isKillswitchEnabled) stringResource(R.string.lan_traffic_blocked) else stringResource(R.string.lan_traffic_allowed),
            checked = isKillswitchEnabled,
            enabled = isVpnModeEnabled,
            onCheckedChange = {
                onKillswitchChange(it)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )

        ExpandableInfoRow(
            icon = Icons.Rounded.SettingsEthernet,
            label = stringResource(R.string.advanced_network),
            description = stringResource(R.string.advanced_network_desc),
            expanded = isAdvancedNetworkExpanded && isVpnModeEnabled,
            enabled = isVpnModeEnabled,
            onExpandChange = {
                if (isVpnModeEnabled) {
                    onAdvancedNetworkExpandedChange(it)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        ) {
            Column(modifier = Modifier.padding(start = 56.dp, top = 4.dp, bottom = 8.dp, end = 4.dp)) {

                val isMtuError = customMtu.isNotEmpty() && (customMtu.toIntOrNull()?.let { it !in 1280..1500 } ?: true)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "mtu",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.weight(1f))

                    CompactTextField(
                        value = customMtu,
                        onValueChange = { onCustomMtuChange(it.filter { c -> c.isDigit() }.take(4)) },
                        placeholder = stringResource(R.string.mtu_auto),
                        isError = isMtuError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(72.dp).height(40.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    stringResource(R.string.dns),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))

                val dnsLabels = listOf("cf", "google", "quad9", "adguard", stringResource(R.string.custom))
                val dnsEnabled = isKillswitchEnabled

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (dnsEnabled) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                ) {
                    val segmentWidth = maxWidth / 5
                    val dnsIndicatorOffset by animateDpAsState(
                        targetValue = segmentWidth * dnsPreset,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "dns_indicator"
                    )

                    if (dnsEnabled) {
                        Box(
                            modifier = Modifier
                                .offset(x = dnsIndicatorOffset)
                                .width(segmentWidth)
                                .fillMaxHeight()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                    }

                    Row(modifier = Modifier.fillMaxSize()) {
                        dnsLabels.forEachIndexed { index, label ->
                            val isSelected = dnsPreset == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        enabled = dnsEnabled,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        onDnsPresetChange(index)
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    color = when {
                                        !dnsEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = dnsPreset == 4 && dnsEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        val isDnsError = customDns.isNotEmpty() && !NetworkUtils.isValidDnsServers(customDns)
                        CompactTextField(
                            value = customDns,
                            onValueChange = onCustomDnsChange,
                            placeholder = "1.1.1.1, 8.8.8.8",
                            isError = isDnsError,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = !dnsEnabled) {
                    Text(
                        stringResource(R.string.dns_system_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.addresses),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.addresses_random),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Spacer(Modifier.height(4.dp))

                val isAddressError = customAddresses.isNotEmpty() && !NetworkUtils.isValidAddresses(customAddresses)

                CompactTextField(
                    value = customAddresses,
                    onValueChange = onCustomAddressesChange,
                    placeholder = "10.x.x.x/32, fdxx::x/128",
                    isError = isAddressError,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                )
            }
        }

        ExpandableInfoRow(
            icon = Icons.Rounded.Share,
            label = stringResource(R.string.socks5_proxy),
            description = if (isLanSharingEnabled) stringResource(R.string.socks_lan_share, socksPort) else stringResource(R.string.socks_local, socksPort),
            expanded = isSocks5Expanded,
            onExpandChange = {
                onSocks5ExpandedChange(it)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        ) {
            Column(modifier = Modifier.padding(start = 56.dp, top = 4.dp, bottom = 8.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.allow_lan_connections), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isLanSharingEnabled,
                        onCheckedChange = onLanSharingChange,
                        modifier = Modifier.scale(0.8f)
                    )
                }
                Spacer(Modifier.height(8.dp))

                val isPortError = socksPort.toIntOrNull().let { it == null || it !in 1024..65535 }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = socksPort,
                        onValueChange = onSocksPortChange,
                        label = { Text(stringResource(R.string.port), fontSize = 12.sp) },
                        isError = isPortError,
                        supportingText = if (isPortError) {
                            { Text(stringResource(R.string.port_allowed_range)) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.width(12.dp))

                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSocksPortChange("55555")
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = Color.Transparent,
                        modifier = Modifier
                            .offset(y = 3.dp)
                            .size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.reset), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                val hasChanges = (socksPort != appliedSocksPort || isLanSharingEnabled != appliedLanSharingEnabled)

                AnimatedVisibility(
                    visible = (activeLink != null) && hasChanges && !isPortError,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRestartCore()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.apply_restart_core), fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (updateInfo != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onUpdateClick() }
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val context = LocalContext.current
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .components {
                        if (Build.VERSION.SDK_INT >= 28) {
                            add(ImageDecoderDecoder.Factory())
                        } else {
                            add(GifDecoder.Factory())
                        }
                    }
                    .build()
            }

            AsyncImage(
                model = R.drawable.logo,
                contentDescription = null,
                imageLoader = imageLoader,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(6.dp))

            if (updateInfo != null) {
                Text(
                    text = "@corgisolutions · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = 10.sp,
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    text = " → ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
                Text(
                    text = "v${updateInfo.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 10.sp
                )
            } else {
                Text(
                    text = "@corgisolutions · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {

    val interactionSource = remember { MutableInteractionSource() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                placeholder = if (placeholder.isNotEmpty()) {
                    {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            )
        }
    )
}