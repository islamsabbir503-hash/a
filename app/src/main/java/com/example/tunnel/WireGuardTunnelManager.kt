package com.example.tunnel

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

object WireGuardTunnelManager {
    private const val TAG = "WireGuardTunnelMgr"
    private var backend: GoBackend? = null
    private var isInitialized = false

    private val tunnel = object : Tunnel {
        override fun getName(): String = "NitroWarp"
        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "WireGuard Tunnel state changed to: $newState")
        }
    }

    fun init(context: Context) {
        if (!isInitialized) {
            try {
                backend = GoBackend(context.applicationContext)
                isInitialized = true
                Log.d(TAG, "WireGuard GoBackend initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize WireGuard GoBackend", e)
            }
        }
    }

    fun generateKeyPair(): Pair<String, String> {
        val keyPair = KeyPair()
        return Pair(keyPair.privateKey.toBase64(), keyPair.publicKey.toBase64())
    }

    suspend fun connect(configString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val b = backend ?: throw IllegalStateException("WireGuard GoBackend is not initialized")
            Log.d(TAG, "Parsing WireGuard config for Cloudflare Turbo:\n$configString")
            val config = Config.parse(ByteArrayInputStream(configString.toByteArray(StandardCharsets.UTF_8)))
            Log.d(TAG, "Activating native WireGuard tunnel...")
            val newState = b.setState(tunnel, Tunnel.State.UP, config)
            Log.d(TAG, "Native WireGuard tunnel activated with state: $newState")
            newState == Tunnel.State.UP
        } catch (e: Exception) {
            Log.e(TAG, "WireGuard connection failed", e)
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            backend?.setState(tunnel, Tunnel.State.DOWN, null)
            Log.d(TAG, "WireGuard tunnel deactivated (State.DOWN)")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting WireGuard tunnel", e)
        }
    }

    fun isConnected(): Boolean {
        return try {
            backend?.getState(tunnel) == Tunnel.State.UP
        } catch (_: Exception) {
            false
        }
    }

    fun getStatistics(): Statistics? {
        return try {
            backend?.getStatistics(tunnel)
        } catch (_: Exception) {
            null
        }
    }
}
