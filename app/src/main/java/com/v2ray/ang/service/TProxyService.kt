package com.v2ray.ang.service

@Suppress("FunctionName")
class TProxyService {
    companion object {
        @JvmStatic
        external fun TProxyStartService(configPath: String, fd: Int)

        @JvmStatic
        external fun TProxyStopService()

        @JvmStatic
        // do NOT trust this hoe saying this is unused! the native layer needs it. it WILL crash!
        external fun TProxyGetStats(): LongArray?

        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }
}
