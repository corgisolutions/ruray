package io.github.corgisolutions.ruray.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vless_hosts")
data class VlessHost(
    @PrimaryKey val link: String,
    val latency: Long = -1,
    val lastChecked: Long = 0,
    val isWorking: Boolean = false,
    val failureCount: Int = 0,
    val countryCode: String? = null
)
