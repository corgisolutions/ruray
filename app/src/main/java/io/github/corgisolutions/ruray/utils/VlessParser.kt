package io.github.corgisolutions.ruray.utils

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import io.github.corgisolutions.ruray.data.*
import java.net.URLDecoder
import androidx.core.net.toUri

object VlessParser {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun parseVlessUrlToConfig(vlessUrl: String, localPort: Int = 10808, listenAddr: String = "127.0.0.1"): String? {
        try {
            if (!vlessUrl.startsWith("vless://")) return null

            val uri = vlessUrl.toUri()
            val userInfo = uri.userInfo ?: return null
            val host = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: 443

            val type = uri.getQueryParameter("type") ?: "tcp"
            val security = uri.getQueryParameter("security") ?: "none"
            val fp = uri.getQueryParameter("fp") ?: "chrome"
            val sni = uri.getQueryParameter("sni") ?: ""
            val pbk = uri.getQueryParameter("pbk") ?: ""
            val sid = uri.getQueryParameter("sid") ?: ""
            val spx = uri.getQueryParameter("spx") ?: ""
            val flow = uri.getQueryParameter("flow") ?: ""
            val path = uri.getQueryParameter("path") ?: "/"
            val hostParam = uri.getQueryParameter("host") ?: ""
            val alpn = uri.getQueryParameter("alpn") ?: ""
            val mode = uri.getQueryParameter("mode") ?: "auto"
            val serviceName = uri.getQueryParameter("serviceName") ?: ""
            //val authority = uri.getQueryParameter("authority") ?: ""
            val extraJson = uri.getQueryParameter("extra") ?: ""
            val allowInsecure = uri.getQueryParameter("allowInsecure") == "1"

            val user = UserBean(id = userInfo, encryption = "none", flow = flow.ifEmpty { null })
            val vnext = VnextBean(address = host, port = port, users = listOf(user))
            
            var realitySettings: RealitySettingsBean? = null
            var tlsSettings: TlsSettingsBean? = null
            var wsSettings: WsSettingsBean? = null
            var grpcSettings: GrpcSettingsBean? = null
            var xhttpSettings: XhttpSettingsBean? = null

            if (security.equals("reality", ignoreCase = true)) {
                realitySettings = RealitySettingsBean(
                    fingerprint = fp,
                    serverName = sni,
                    publicKey = pbk,
                    shortId = sid,
                    spiderX = spx.ifEmpty { null },
                    show = false
                )
            } else if (security.equals("tls", ignoreCase = true)) {
                tlsSettings = TlsSettingsBean(
                    serverName = sni.ifEmpty { hostParam.ifEmpty { host } },
                    alpn = if (alpn.isNotEmpty()) URLDecoder.decode(alpn, "UTF-8").split(",") else null,
                    fingerprint = fp,
                    allowInsecure = allowInsecure
                )
            }

            if (type.equals("ws", ignoreCase = true)) {
                val headers = mutableMapOf<String, String>()
                if (hostParam.isNotEmpty()) {
                    headers["Host"] = hostParam
                }
                wsSettings = WsSettingsBean(
                    path = URLDecoder.decode(path, "UTF-8"),
                    headers = headers.ifEmpty { null }
                )
            } else if (type.equals("grpc", ignoreCase = true)) {
                val isMulti = mode.equals("gun", ignoreCase = true) || mode.equals("multi", ignoreCase = true)
                grpcSettings = GrpcSettingsBean(
                    serviceName = serviceName.ifEmpty { "grpc" }, // default
                    multiMode = isMulti
                )
            } else if (type.equals("xhttp", ignoreCase = true)) {
                 val extraMap: Map<String, Any>? = if (extraJson.isNotEmpty()) {
                     try {
                         val type = object : TypeToken<Map<String, Any>>() {}.type
                         gson.fromJson(URLDecoder.decode(extraJson, "UTF-8"), type)
                     } catch (_: Exception) { null }
                 } else null

                 xhttpSettings = XhttpSettingsBean(
                     path = URLDecoder.decode(path, "UTF-8"),
                     host = hostParam.ifEmpty { null },
                     mode = mode,
                     extra = extraMap
                 )
            }

            val streamSettings = StreamSettingsBean(
                network = type,
                security = security,
                realitySettings = realitySettings,
                tlsSettings = tlsSettings,
                wsSettings = wsSettings,
                grpcSettings = grpcSettings,
                xhttpSettings = xhttpSettings,
                sockopt = SockoptBean(tcpFastOpen = true)
            )

            val outbound = OutboundBean(
                tag = "proxy",
                protocol = "vless",
                settings = OutSettingsBean(vnext = listOf(vnext)),
                streamSettings = streamSettings,
                mux = MuxBean(enabled = false)
            )

            val inbound = InboundBean(
                tag = "socks-in",
                port = localPort,
                protocol = "socks",
                listen = listenAddr,
                settings = InboundSettings(auth = "noauth", udp = true),
                sniffing = SniffingBean(enabled = true, destOverride = listOf("http", "tls", "quic"))
            )

            val config = XrayConfig(
                log = LogBean(loglevel = "warn"),
                inbounds = listOf(inbound),
                outbounds = listOf(outbound)
            )

            return gson.toJson(config)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
