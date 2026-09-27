package com.example.tunnel

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.util.SharedLibraryLoader
import com.wireguard.crypto.Key
import java.lang.reflect.Method

object NativeWireGuardBridge {
    private const val TAG = "NativeWireGuardBridge"

    @Volatile
    private var isLoaded = false

    private var wgTurnOnMethod: Method? = null
    private var wgTurnOffMethod: Method? = null
    private var wgGetSocketV4Method: Method? = null
    private var wgGetSocketV6Method: Method? = null
    private var wgGetConfigMethod: Method? = null
    private var wgVersionMethod: Method? = null

    @Synchronized
    fun initialize(context: Context) {
        if (isLoaded) return
        try {
            SharedLibraryLoader.loadSharedLibrary(context.applicationContext, "wg-go")
            val clazz = GoBackend::class.java

            wgTurnOnMethod = clazz.getDeclaredMethod(
                "wgTurnOn",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            ).apply { isAccessible = true }

            wgTurnOffMethod = clazz.getDeclaredMethod(
                "wgTurnOff",
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            wgGetSocketV4Method = clazz.getDeclaredMethod(
                "wgGetSocketV4",
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            wgGetSocketV6Method = clazz.getDeclaredMethod(
                "wgGetSocketV6",
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            wgGetConfigMethod = clazz.getDeclaredMethod(
                "wgGetConfig",
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            wgVersionMethod = clazz.getDeclaredMethod(
                "wgVersion"
            ).apply { isAccessible = true }

            val version = wgVersionMethod?.invoke(null) as? String
            Log.i(TAG, "Native wg-go library loaded successfully, version: $version")
            isLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native wg-go bridge: ${e.message}", e)
            throw e
        }
    }

    fun turnOn(ifName: String, tunFd: Int, uapiSettings: String): Int {
        val method = wgTurnOnMethod ?: throw IllegalStateException("wg-go JNI not initialized")
        return method.invoke(null, ifName, tunFd, uapiSettings) as Int
    }

    fun turnOff(handle: Int) {
        val method = wgTurnOffMethod ?: return
        if (handle >= 0) {
            try {
                method.invoke(null, handle)
            } catch (e: Exception) {
                Log.e(TAG, "Error turning off tunnel handle $handle: ${e.message}", e)
            }
        }
    }

    fun getSocketV4(handle: Int): Int {
        val method = wgGetSocketV4Method ?: return -1
        return try {
            method.invoke(null, handle) as Int
        } catch (_: Exception) {
            -1
        }
    }

    fun getSocketV6(handle: Int): Int {
        val method = wgGetSocketV6Method ?: return -1
        return try {
            method.invoke(null, handle) as Int
        } catch (_: Exception) {
            -1
        }
    }

    fun getConfig(handle: Int): String? {
        val method = wgGetConfigMethod ?: return null
        return try {
            method.invoke(null, handle) as? String
        } catch (_: Exception) {
            null
        }
    }

    data class TrafficStats(val rxBytes: Long, val txBytes: Long)

    fun getTrafficStats(handle: Int): TrafficStats? {
        if (handle < 0) return null
        val conf = getConfig(handle) ?: return null
        var rx = 0L
        var tx = 0L
        for (line in conf.lineSequence()) {
            if (line.startsWith("rx_bytes=")) {
                rx = line.substringAfter("rx_bytes=").trim().toLongOrNull() ?: rx
            } else if (line.startsWith("tx_bytes=")) {
                tx = line.substringAfter("tx_bytes=").trim().toLongOrNull() ?: tx
            }
        }
        return TrafficStats(rx, tx)
    }

    fun base64KeyToHex(base64Key: String): String {
        return Key.fromBase64(base64Key).toHex()
    }
}
