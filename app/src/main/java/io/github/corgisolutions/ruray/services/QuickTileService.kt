package io.github.corgisolutions.ruray.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.corgisolutions.ruray.MainActivity
import io.github.corgisolutions.ruray.R
import io.github.corgisolutions.ruray.utils.LocaleHelper

class QuickTileService : TileService() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val localizedContext = LocaleHelper.onAttach(this)

        val prefs = getSharedPreferences("proxy_state", MODE_PRIVATE)
        val activeLink = prefs.getString("active_link", null)

        if (activeLink != null) {
            val intent = Intent(this, ProxyService::class.java)
            intent.action = "STOP"
            startService(intent)

            tile.state = Tile.STATE_INACTIVE
            tile.label = "RURay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = localizedContext.getString(R.string.tile_disconnected)
            }
            tile.updateTile()
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                val appIntent = Intent(this, MainActivity::class.java)
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appIntent.putExtra("REQUEST_VPN", true)

                if (Build.VERSION.SDK_INT >= 34) {
                    val pendingIntent = PendingIntent.getActivity(
                        this, 0, appIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(appIntent)
                }
                return
            }

            val intent = Intent(this, ProxyService::class.java)
            intent.action = "SCAN_AND_CONNECT"
            startForegroundService(intent)

            tile.state = Tile.STATE_ACTIVE
            tile.label = "RURay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = localizedContext.getString(R.string.tile_connecting)
            }
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val localizedContext = LocaleHelper.onAttach(this)

        val prefs = getSharedPreferences("proxy_state", MODE_PRIVATE)
        val activeLink = prefs.getString("active_link", null)

        if (activeLink != null) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "RURay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = localizedContext.getString(R.string.tile_connected)
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "RURay"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = localizedContext.getString(R.string.tile_tap_to_connect)
            }
        }
        tile.updateTile()
    }
}