package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.tunnel.NativeWireGuardBridge
import com.wireguard.android.util.SharedLibraryLoader
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class NitroWarpVpnService : VpnService() {

    companion object {
        const val TAG = "NitroWarpVpnService"
        const val ACTION_CONNECT = "com.sabbitx10.nitrowarpvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.sabbitx10.nitrowarpvpn.DISCONNECT"

        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_ENGINE = "extra_engine"
        const val EXTRA_CLIENT_IP = "extra_client_ip"
        const val EXTRA_PREFIX_LENGTH = "extra_prefix_length"
        const val EXTRA_MTU = "extra_mtu"
        const val EXTRA_PRIVATE_KEY_B64 = "extra_private_key_b64"
        const val EXTRA_SERVER_PUB_KEY_B64 = "extra_server_pub_key_b64"
        const val EXTRA_ENDPOINT = "extra_endpoint"

        const val EXTRA_SESSION_NAME = EXTRA_SESSION
        const val EXTRA_PRIVATE_KEY = EXTRA_PRIVATE_KEY_B64
        const val EXTRA_SERVER_PUBLIC_KEY = EXTRA_SERVER_PUB_KEY_B64
        const val EXTRA_SERVER_IP = "extra_server_ip"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_PROTOCOL = "extra_protocol"
        const val EXTRA_CIPHER = "extra_cipher"
        const val EXTRA_COUNTRY_LONG = "extra_country_long"
        const val EXTRA_COUNTRY_SHORT = "extra_country_short"
        const val EXTRA_OVPN_BASE64 = "extra_ovpn_base64"

        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "nitrowarp_vpn_service_channel"
        private const val PREFS_NAME = "nitrowarp_verified_wg_prefs"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var activeTunnelHandle: Int = -1
            private set

        @Volatile
        var lastErrorMessage: String? = null
            private set

        @Volatile
        var currentOpenVpnState: String = "DISCONNECTED"
            private set

        fun getActiveTrafficStats(): NativeWireGuardBridge.TrafficStats? {
            val handle = activeTunnelHandle
            return if (handle in 0..9998) {
                NativeWireGuardBridge.getTrafficStats(handle)
            } else {
                null
            }
        }
    }

    private val tunnelExecutor = Executors.newSingleThreadExecutor()
    private val globalConnectingFlag = AtomicBoolean(false)

    private val openVpnStateListener = object : VpnStatus.StateListener {
        override fun updateState(
            state: String?,
            logmessage: String?,
            localizedResId: Int,
            level: ConnectionStatus?,
            intent: Intent?
        ) {
            val stateName = state ?: level?.name ?: ""
            handleOpenVpnStateChange(stateName, level)
        }

        override fun setConnectedVPN(uuid: String?) {}
    }

    private val openVpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getStringExtra("state") ?: intent?.getStringExtra("detailstatus") ?: return
            handleOpenVpnStateChange(state, null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            SharedLibraryLoader.loadSharedLibrary(this, "wg-go")
            NativeWireGuardBridge.initialize(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading native wg-go library: ${e.message}", e)
        }
        registerOpenVpnStateHooks()
        startInForeground("NitroWarp VPN", "Initializing tunnel engine...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (intent.action == ACTION_DISCONNECT) {
            globalConnectingFlag.set(false)
            tunnelExecutor.execute { disconnectTunnel() }
            return START_NOT_STICKY
        }

        val sessionName = intent.getStringExtra(EXTRA_SESSION) ?: "NitroWarp VPN"
        val engine = intent.getStringExtra(EXTRA_ENGINE) ?: "TURBO"
        val rawClientIp = intent.getStringExtra(EXTRA_CLIENT_IP) ?: "172.16.0.2"
        val prefixLength = intent.getIntExtra(EXTRA_PREFIX_LENGTH, 32)
        val mtu = intent.getIntExtra(EXTRA_MTU, 1280)
        val privB64 = intent.getStringExtra(EXTRA_PRIVATE_KEY_B64) ?: ""
        val serverPubB64 = intent.getStringExtra(EXTRA_SERVER_PUB_KEY_B64) ?: ""
        val rawEndpoint = intent.getStringExtra(EXTRA_ENDPOINT) ?: "162.159.192.1:2408"

        val serverIp = intent.getStringExtra(EXTRA_SERVER_IP) ?: ""
        val serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, 443)
        val protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "TCP"
        val countryLong = intent.getStringExtra(EXTRA_COUNTRY_LONG) ?: ""
        val ovpnBase64 = intent.getStringExtra(EXTRA_OVPN_BASE64) ?: ""

        lastErrorMessage = null
        startInForeground("NitroWarp VPN", "Handshaking with $sessionName...")

        tunnelExecutor.execute {
            // STRICT SEPARATION BETWEEN ENGINE 1 (TURBO) AND ENGINE 2 (GLOBAL)
            if (engine.equals("GLOBAL", ignoreCase = true)) {
                establishGlobalOpenVpnEngine(
                    countryLong = countryLong,
                    serverIp = serverIp,
                    serverPort = serverPort,
                    protocol = protocol,
                    ovpnBase64 = ovpnBase64
                )
            } else {
                establishCloudflareTurboTunnel(
                    sessionName = sessionName,
                    rawClientIp = rawClientIp,
                    prefixLength = prefixLength,
                    mtu = mtu,
                    passedPrivB64 = privB64,
                    passedPubB64 = serverPubB64,
                    passedEndpoint = rawEndpoint
                )
            }
        }

        return START_STICKY
    }

    // =========================================================================================
    // ENGINE 2: NATIVE OPENVPN ENGINE (vpnLib.aar / de.blinkt.openvpn) — ZERO CLOUDFLARE!
    // =========================================================================================
    private fun establishGlobalOpenVpnEngine(
        countryLong: String,
        serverIp: String,
        serverPort: Int,
        protocol: String,
        ovpnBase64: String
    ) {
        cleanupExistingTunnelOnly()

        if (serverIp.isBlank() || ovpnBase64.isBlank()) {
            lastErrorMessage = "Selected Global server ($countryLong) has no OpenVPN profile."
            disconnectTunnel()
            return
        }

        try {
            val cleanB64 = ovpnBase64.replace("\\s+".toRegex(), "")
            val rawOvpnText = String(Base64.decode(cleanB64, Base64.DEFAULT), Charsets.UTF_8)
            val preparedOvpnConfig = sanitizeOvpnConfigForEngine(rawOvpnText)

            currentOpenVpnState = "CONNECTING"
            globalConnectingFlag.set(true)

            val cp = ConfigParser()
            cp.parseConfig(StringReader(preparedOvpnConfig))
            val profile: VpnProfile = cp.convertProfile()
            val countryLabel = countryLong.ifBlank { serverIp }
            profile.mName = countryLabel
            profile.mUsername = "vpn"
            profile.mPassword = "vpn"

            val pm = ProfileManager.getInstance(applicationContext)
            pm.addProfile(profile)
            pm.saveProfile(applicationContext, profile)
            ProfileManager.setTemporaryProfile(applicationContext, profile)

            VPNLaunchHelper.startOpenVpn(profile, applicationContext)

            val deadline = System.currentTimeMillis() + 22000L
            var connected = false

            while (System.currentTimeMillis() < deadline && globalConnectingFlag.get()) {
                val st = currentOpenVpnState.uppercase(Locale.US)
                if (st == "CONNECTED" || isNativeOpenVpnTunActive()) {
                    connected = true
                    break
                }
                if (st == "AUTH_FAILED" || st == "NONETWORK" || st == "ERROR") {
                    lastErrorMessage = "$countryLong ($serverIp) rejected connection ($st)."
                    break
                }
                Thread.sleep(250)
            }

            if (!connected) {
                if (lastErrorMessage.isNullOrBlank()) {
                    lastErrorMessage = "$countryLong ($serverIp:$serverPort) timed out. Stayed disconnected."
                }
                disconnectTunnel()
                return
            }

            activeTunnelHandle = 9999
            isRunning = true

            val activeNotification = buildForegroundNotification(
                "NitroWarp VPN • Protected",
                "🌍 $countryLong Active ($serverIp • OpenVPN $protocol:$serverPort)"
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, activeNotification)

            Log.i(TAG, "Native OpenVPN Engine CONNECTED to $countryLong ($serverIp:$serverPort)")
        } catch (e: Exception) {
            Log.e(TAG, "Global OpenVPN Engine error: ${e.message}", e)
            lastErrorMessage = "Global OpenVPN error: ${e.message}"
            disconnectTunnel()
        }
    }

    /**
     * Ensures VPN Gate .ovpn config is compatible with all ics-openvpn builds inside vpnLib.aar
     */
    private fun sanitizeOvpnConfigForEngine(rawConfig: String): String {
        val sb = StringBuilder()
        var hasDataCiphers = false
        for (line in rawConfig.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.startsWith(";")) continue
            if (trimmed.startsWith("data-ciphers", ignoreCase = true)) {
                hasDataCiphers = true
            }
            sb.append(line).append("\n")
        }
        if (!hasDataCiphers) {
            sb.append("data-ciphers AES-128-CBC:AES-256-CBC:AES-128-GCM:AES-256-GCM:BF-CBC\n")
            sb.append("data-ciphers-fallback AES-128-CBC\n")
        }
        return sb.toString()
    }

    private fun stopNativeOpenVpnEngine() {
        try {
            ProfileManager.setConntectedVpnProfileDisconnected(applicationContext)
        } catch (_: Exception) {}

        try {
            val disconnectIntent = Intent(applicationContext, OpenVPNService::class.java).apply {
                action = OpenVPNService.DISCONNECT_VPN
            }
            startService(disconnectIntent)
        } catch (_: Exception) {}

        try {
            stopService(Intent(applicationContext, OpenVPNService::class.java))
        } catch (_: Exception) {}

        currentOpenVpnState = "DISCONNECTED"
    }

    private fun registerOpenVpnStateHooks() {
        try {
            val filter = IntentFilter("connectionState").apply {
                addAction("de.blinkt.openvpn.VPN_STATUS")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(openVpnReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(openVpnReceiver, filter)
            }
        } catch (_: Exception) {}

        try {
            VpnStatus.addStateListener(openVpnStateListener)
        } catch (e: Exception) {
            Log.w(TAG, "VpnStatus.addStateListener notice: ${e.message}")
        }
    }

    private fun unregisterOpenVpnStateHooks() {
        try {
            unregisterReceiver(openVpnReceiver)
        } catch (_: Exception) {}

        try {
            VpnStatus.removeStateListener(openVpnStateListener)
        } catch (_: Exception) {}
    }

    private fun handleOpenVpnStateChange(rawState: String, level: ConnectionStatus? = null) {
        val state = rawState.trim().uppercase(Locale.US)
        if (state.isEmpty() && level == null) return
        currentOpenVpnState = if (state.isNotEmpty()) state else (level?.name ?: "DISCONNECTED")
        Log.d(TAG, "OpenVPN Engine State -> $currentOpenVpnState (level=$level)")

        if (level == ConnectionStatus.LEVEL_CONNECTED || state == "CONNECTED") {
            activeTunnelHandle = 9999
            isRunning = true
            currentOpenVpnState = "CONNECTED"
        } else if ((level == ConnectionStatus.LEVEL_NOTCONNECTED ||
                level == ConnectionStatus.LEVEL_AUTH_FAILED ||
                level == ConnectionStatus.LEVEL_NONETWORK ||
                state == "DISCONNECTED" || state == "NOPROCESS" || state == "EXITING") &&
            activeTunnelHandle == 9999 && !globalConnectingFlag.get()
        ) {
            isRunning = false
            activeTunnelHandle = -1
        }
    }

    /**
     * Checks if the OpenVPN TUN interface (tun0 / 10.x.x.x) is live on the device
     * while WireGuard is NOT active.
     */
    private fun isNativeOpenVpnTunActive(): Boolean {
        return try {
            if (VpnStatus.isVPNActive() || VpnStatus.lastLevel() == ConnectionStatus.LEVEL_CONNECTED) {
                return true
            }
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val hasTunIp = interfaces.any { nif ->
                nif.isUp && (nif.name.startsWith("tun", ignoreCase = true) || nif.name.startsWith("tap", ignoreCase = true)) &&
                    Collections.list(nif.inetAddresses).any { addr ->
                        addr is Inet4Address && !addr.isLoopbackAddress && addr.hostAddress != "172.16.0.2"
                    }
            }
            if (hasTunIp) return true

            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && currentOpenVpnState.equals("CONNECTED", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================================
    // ENGINE 1: CLOUDFLARE TURBO (WIREGUARD) — ONLY RUNS WHEN TURBO IS SELECTED
    // =========================================================================================
    private data class VerifiedWarpCredentials(
        val privateKeyB64: String,
        val serverPublicKeyB64: String,
        val clientIpv4: String,
        val endpointIpv4Port: String
    )

    private fun ensureVerifiedWarpCredentials(
        passedPrivB64: String,
        passedPubB64: String,
        rawClientIp: String
    ): VerifiedWarpCredentials {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPriv = prefs.getString("priv_b64", null)
        val savedPub = prefs.getString("pub_b64", null)
        val savedIp = prefs.getString("client_ip", null)
        val savedEndpoint = prefs.getString("endpoint", null)

        if (!savedPriv.isNullOrEmpty() && !savedPub.isNullOrEmpty() && !savedIp.isNullOrEmpty()) {
            return VerifiedWarpCredentials(
                privateKeyB64 = savedPriv,
                serverPublicKeyB64 = savedPub,
                clientIpv4 = sanitizeClientIpv4(savedIp),
                endpointIpv4Port = resolveNumericEndpoint(savedEndpoint ?: "162.159.192.1:2408")
            )
        }

        try {
            val keyPair = KeyPair()
            val privB64 = keyPair.privateKey.toBase64()
            val pubB64 = keyPair.publicKey.toBase64()
            val installId = UUID.randomUUID().toString().replace("-", "").take(22)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val payload = JSONObject().apply {
                put("key", pubB64)
                put("install_id", installId)
                put("fcm_token", "$installId:APA91b${UUID.randomUUID().toString().replace("-", "")}")
                put("tos", sdf.format(Date()))
                put("model", "Android")
                put("type", "Android")
                put("locale", "en_US")
            }

            val url = URL("https://api.cloudflareclient.com/v0a2158/reg")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "okhttp/3.12.1")
                setRequestProperty("CF-Client-Version", "a-6.10-2158")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(responseStr)
                val configObj = json.optJSONObject("result")?.optJSONObject("config")
                    ?: json.optJSONObject("config")

                if (configObj != null) {
                    val peer0 = configObj.optJSONArray("peers")?.optJSONObject(0)
                    val srvPub = peer0?.optString("public_key")
                        ?.takeIf { it.isNotEmpty() }
                        ?: "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
                    val rawV4Endpoint = peer0?.optJSONObject("endpoint")?.optString("v4")
                        ?: "162.159.192.1:2408"
                    val v4Addr = configObj.optJSONObject("interface")
                        ?.optJSONObject("addresses")
                        ?.optString("v4")
                        ?.takeIf { it.isNotEmpty() }
                        ?: "172.16.0.2"

                    val cleanIp = sanitizeClientIpv4(v4Addr)
                    val cleanEndpoint = resolveNumericEndpoint(rawV4Endpoint)

                    prefs.edit()
                        .putString("priv_b64", privB64)
                        .putString("pub_b64", srvPub)
                        .putString("client_ip", cleanIp)
                        .putString("endpoint", cleanEndpoint)
                        .apply()

                    return VerifiedWarpCredentials(privB64, srvPub, cleanIp, cleanEndpoint)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct WARP registration notice: ${e.message}")
        }

        val fallbackPriv = if (passedPrivB64.isNotEmpty()) passedPrivB64 else KeyPair().privateKey.toBase64()
        return VerifiedWarpCredentials(
            privateKeyB64 = fallbackPriv,
            serverPublicKeyB64 = passedPubB64.ifEmpty { "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=" },
            clientIpv4 = sanitizeClientIpv4(rawClientIp),
            endpointIpv4Port = "162.159.192.1:2408"
        )
    }

    private fun sanitizeClientIpv4(rawIp: String): String {
        val firstPart = rawIp.split(",")[0].trim()
        val ipOnly = firstPart.substringBefore("/").trim()
        return if (ipOnly.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")) && ipOnly != "10.8.0.2") {
            ipOnly
        } else {
            "172.16.0.2"
        }
    }

    private fun resolveNumericEndpoint(rawEndpoint: String): String {
        return try {
            val hostPart = rawEndpoint.substringBefore(":").trim().ifEmpty { "162.159.192.1" }
            val portPart = rawEndpoint.substringAfter(":", "2408").trim().toIntOrNull() ?: 2408
            val validPort = if (portPart <= 0) 2408 else portPart

            val resolvedIp = if (hostPart.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
                hostPart
            } else {
                val addresses = InetAddress.getAllByName(hostPart)
                addresses.firstOrNull { it is Inet4Address }?.hostAddress ?: "162.159.192.1"
            }
            "$resolvedIp:$validPort"
        } catch (_: Exception) {
            "162.159.192.1:2408"
        }
    }

    private fun establishCloudflareTurboTunnel(
        sessionName: String,
        rawClientIp: String,
        prefixLength: Int,
        mtu: Int,
        passedPrivB64: String,
        passedPubB64: String,
        passedEndpoint: String
    ) {
        cleanupExistingTunnelOnly()

        try {
            val creds = ensureVerifiedWarpCredentials(passedPrivB64, passedPubB64, rawClientIp)
            val privHex = Key.fromBase64(creds.privateKeyB64).toHex()
            val pubHex = Key.fromBase64(creds.serverPublicKeyB64).toHex()
            val endpoint = creds.endpointIpv4Port
            val clientIpv4 = creds.clientIpv4

            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cm.activeNetwork?.let { setUnderlyingNetworks(arrayOf(it)) }
                }
            } catch (_: Exception) {}

            val safeMtu = if (mtu in 1280..1500) mtu else 1280
            val builder = Builder()
                .setSession(sessionName)
                .setMtu(safeMtu)
                .addAddress(clientIpv4, 32)
                .addDnsServer("1.1.1.1")
                .addDnsServer("1.0.0.1")
                .addRoute("0.0.0.0", 0)
                .setBlocking(true)

            val pfd: ParcelFileDescriptor = builder.establish()
                ?: throw IllegalStateException("VpnService.Builder.establish() returned null")

            val tunFd = pfd.detachFd()

            val uapiConfig = buildString {
                append("private_key=").append(privHex).append("\n")
                append("replace_peers=true\n")
                append("public_key=").append(pubHex).append("\n")
                append("endpoint=").append(endpoint).append("\n")
                append("allowed_ip=0.0.0.0/0\n")
                append("persistent_keepalive_interval=25\n")
            }

            val handle = NativeWireGuardBridge.turnOn("nitrowarp0", tunFd, uapiConfig)
            if (handle < 0) {
                throw IllegalStateException("wgTurnOn failed with code $handle")
            }

            activeTunnelHandle = handle
            isRunning = true

            val sockV4 = NativeWireGuardBridge.getSocketV4(handle)
            if (sockV4 >= 0) {
                protect(sockV4)
            }
            val sockV6 = NativeWireGuardBridge.getSocketV6(handle)
            if (sockV6 >= 0) {
                protect(sockV6)
            }

            val activeNotification = buildForegroundNotification(
                "NitroWarp VPN • Protected",
                "⚡ Cloudflare Turbo Active ($clientIpv4)"
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, activeNotification)

            Log.i(TAG, "Cloudflare Turbo Tunnel UP! handle=$handle, clientIp=$clientIpv4")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish Cloudflare Turbo tunnel: ${e.message}", e)
            lastErrorMessage = "Cloudflare Turbo failed: ${e.message}"
            disconnectTunnel()
        }
    }

    private fun cleanupExistingTunnelOnly() {
        globalConnectingFlag.set(false)
        val handle = activeTunnelHandle
        if (handle in 0..9998) {
            try {
                NativeWireGuardBridge.turnOff(handle)
            } catch (e: Exception) {
                Log.w(TAG, "Error turning off handle $handle: ${e.message}")
            }
        } else if (handle == 9999 || currentOpenVpnState != "DISCONNECTED") {
            stopNativeOpenVpnEngine()
        }
        activeTunnelHandle = -1
        isRunning = false
    }

    private fun disconnectTunnel() {
        cleanupExistingTunnelOnly()
        stopNativeOpenVpnEngine()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                setUnderlyingNetworks(null)
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startInForeground(title: String, text: String) {
        val notification = buildForegroundNotification(title, text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }
    }

    private fun buildForegroundNotification(title: String, text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NitroWarp VPN Tunnel Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for NitroWarp VPN active tunnel"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterOpenVpnStateHooks()
        disconnectTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        if (activeTunnelHandle in 0..9998) {
            disconnectTunnel()
        }
        super.onRevoke()
    }
}
