package io.github.corgisolutions.ruray.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "split_tunnel_apps")
data class SplitTunnelApp(
    @PrimaryKey val packageName: String,
    val isAutoAdded: Boolean = false
)