package io.github.corgisolutions.ruray

object Config {
    const val SCAN_CONCURRENCY = 20
    const val BACKGROUND_SCAN_CONCURRENCY = 3
    const val BACKGROUND_SCAN_INTERVAL_MS = 30_000L
    const val KEEPALIVE_INTERVAL_MS = 3000L
    const val KEEPALIVE_FAIL_THRESHOLD = 3
    const val HOST_FAIL_DELETE_THRESHOLD = 3
    const val LATENCY_TIMEOUT_MS = 2000
    const val LATENCY_TIMEOUT_LONG_MS = 3000
    const val XRAY_STARTUP_TIMEOUT_MS = 5000L
    val PORT_RANGE = 50000..60000
    const val DEFAULT_SOCKS_PORT = 55555
}
