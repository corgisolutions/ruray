package io.github.corgisolutions.ruray.data

data class XrayConfig(
    val log: LogBean = LogBean(),
    val inbounds: List<InboundBean> = listOf(),
    val outbounds: List<OutboundBean> = listOf()
)

data class LogBean(
    val loglevel: String = "warning"
)

data class InboundBean(
    val tag: String,
    val port: Int,
    val protocol: String,
    val listen: String = "127.0.0.1",
    val settings: InboundSettings? = null,
    val sniffing: SniffingBean? = null
)

data class InboundSettings(
    val auth: String = "noauth",
    val udp: Boolean = true
)

data class SniffingBean(
    val enabled: Boolean = true,
    val destOverride: List<String> = listOf("http", "tls", "quic")
)

data class OutboundBean(
    val tag: String = "proxy",
    val protocol: String,
    val settings: OutSettingsBean? = null,
    val streamSettings: StreamSettingsBean? = null,
    val mux: MuxBean? = null
)

data class OutSettingsBean(
    val vnext: List<VnextBean>? = null
)

data class VnextBean(
    val address: String,
    val port: Int,
    val users: List<UserBean>
)

data class UserBean(
    val id: String,
    val encryption: String = "none",
    val flow: String? = null
)

data class StreamSettingsBean(
    val network: String,
    val security: String,
    val realitySettings: RealitySettingsBean? = null,
    val tlsSettings: TlsSettingsBean? = null,
    val tcpSettings: TcpSettingsBean? = null,
    val wsSettings: WsSettingsBean? = null,
    val grpcSettings: GrpcSettingsBean? = null,
    val xhttpSettings: XhttpSettingsBean? = null,
    val sockopt: SockoptBean? = null
)

data class RealitySettingsBean(
    val fingerprint: String,
    val serverName: String,
    val publicKey: String,
    val shortId: String,
    val show: Boolean = false,
    val spiderX: String? = null
)

data class TlsSettingsBean(
    val serverName: String? = null,
    val alpn: List<String>? = null,
    val fingerprint: String? = null,
    val allowInsecure: Boolean = false
)

data class TcpSettingsBean(
    val header: HeaderBean? = null
)

data class WsSettingsBean(
    val path: String = "/",
    val headers: Map<String, String>? = null
)

data class GrpcSettingsBean(
    val serviceName: String = "",
    val multiMode: Boolean = false
)

data class XhttpSettingsBean(
    val path: String = "/",
    val host: String? = null,
    val mode: String? = "auto",
    val extra: Map<String, Any>? = null
)

data class HeaderBean(
    val type: String = "none"
)

data class MuxBean(
    val enabled: Boolean = false,
    val concurrency: Int = 8
)

data class SockoptBean(
    val tcpFastOpen: Boolean = true
)
