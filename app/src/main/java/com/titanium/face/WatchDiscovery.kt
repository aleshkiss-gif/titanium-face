package com.titanium.face

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Discovers wireless-debugging TLS endpoints advertised by Wear OS over local Wi-Fi. */
object WatchDiscovery {
    data class Endpoint(val host: String, val port: Int, val name: String)

    fun find(context: Context, preferredHost: String?, timeoutMs: Long = 6500): Endpoint? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val latch = CountDownLatch(1)
        val found = java.util.concurrent.atomic.AtomicReference<Endpoint?>()
        val resolving = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!resolving.compareAndSet(false, true)) return
                @Suppress("DEPRECATION")
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.set(false)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = serviceInfo.host?.hostAddress
                        if (host != null && serviceInfo.port in 1..65535) {
                            val endpoint = Endpoint(host, serviceInfo.port, serviceInfo.serviceName)
                            if (found.get() == null || host == preferredHost) found.set(endpoint)
                            if (host == preferredHost || preferredHost.isNullOrBlank()) latch.countDown()
                        }
                        resolving.set(false)
                    }
                })
            }
        }
        try {
            nsd.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        return found.get()
    }
}
