package io.github.corgisolutions.ruray.utils

import io.github.corgisolutions.ruray.data.db.AppDatabase
import io.github.corgisolutions.ruray.data.db.VlessHost

object HostUpdater {
    suspend fun updateHosts(db: AppDatabase, proxyPort: Int = 0): Int {
        val links = Scraper.fetchLinks(proxyPort = proxyPort)
        if (links.isEmpty()) return 0

        val existingHosts = db.vlessDao().getAllHostsList()
        val existingKeys = existingHosts.mapNotNull {
            val d = parseVlessLink(it.link)
            if (d != null) "${d.uuid}@${d.address}:${d.port}" else null
        }.toSet()

        val newHosts = links.mapNotNull { link ->
            val d = parseVlessLink(link)
            if (d != null) {
                val key = "${d.uuid}@${d.address}:${d.port}"
                if (!existingKeys.contains(key)) VlessHost(link) else null
            } else null
        }.distinctBy {
            val d = parseVlessLink(it.link)
            if (d != null) "${d.uuid}@${d.address}:${d.port}" else it.link
        }

        if (newHosts.isNotEmpty()) {
            db.vlessDao().insertAll(newHosts)
            return newHosts.size
        }
        return 0
    }
}
