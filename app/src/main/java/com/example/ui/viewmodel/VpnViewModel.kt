package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.TrafficStats
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NitroWarpVpnService
import com.example.data.api.ApiClient
import com.example.data.crypto.CryptoUtils
import com.example.data.model.ConnectionState
import com.example.data.model.CountryGroup
import com.example.data.model.SortOption
import com.example.data.model.StatusResponse
import com.example.data.model.TunnelConfig
import com.example.data.model.VpnEngine
import com.example.data.model.VpnServer
import com.example.data.model.WarpGenerateRequest
import com.example.tunnel.WireGuardTunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class VpnUiState(
    val status: StatusResponse? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val activeEngine: VpnEngine = VpnEngine.TURBO,
    val servers: List<VpnServer> = emptyList(),
    val countryGroups: List<CountryGroup> = emptyList(),
    val expandedCountries: Set<String> = emptySet(),
    val favoriteCountries: Set<String> = emptySet(),
    val isFavoritesSectionExpanded: Boolean = true,
    val realUserIp: String = "",
    val realUserCountryCode: String = "BD",
    val selectedServer: VpnServer? = null,
    val handshakeStepText: String = "",
    val sessionSeconds: Long = 0,
    val downloadSpeed: Double = 0.0,
    val uploadSpeed: Double = 0.0,
    val measuredPing: Long = 0,
    val isRefreshingStatus: Boolean = false,
    val isLoadingServers: Boolean = false,
    val isSheetOpen: Boolean = false,
    val searchQuery: String = "",
    val selectedCountryFilter: String = "ALL",
    val sortOption: SortOption = SortOption.PING,
    val toastMessage: String? = null
)

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VpnUiState())
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    private var sessionTimerJob: Job? = null
    private var telemetryJob: Job? = null

    private val approvedServerKeys = mutableSetOf<String>()

    init {
        try {
            val prefs = application.getSharedPreferences("nitrowarp_favorites_prefs", Context.MODE_PRIVATE)
            val savedFavs = prefs.getStringSet("favorite_countries", emptySet()) ?: emptySet()
            _uiState.update { it.copy(favoriteCountries = savedFavs) }
        } catch (_: Exception) {}

        refreshStatus()
        loadServers()
    }

    /**
     * Opens a fresh non-pooled HTTP connection (Connection: close) so Android routes the request
     * through the newly established VPN tunnel instead of reusing pre-VPN SIM sockets.
     */
    private suspend fun fetchFreshTunnelStatus(): Pair<StatusResponse?, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val url = URL("https://nitrowarpvpn.sabbirboos0.workers.dev/api/status?t=${System.currentTimeMillis()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                useCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            if (conn.responseCode in 200..299) {
                val bodyStr = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(bodyStr)
                val ip = json.optString("connected_ip").ifBlank { json.optString("ip") }
                val code = json.optString("country_code", "XX")
                val isp = json.optString("isp", "")
                val colo = json.optString("colo", "DAC")
                val isCf = json.optBoolean("is_cloudflare", false)
                conn.disconnect()
                return@withContext Pair(
                    StatusResponse(
                        ip = ip,
                        connectedIp = ip,
                        countryCode = code,
                        isp = isp,
                        colo = colo,
                        isCloudflare = isCf
                    ),
                    rtt
                )
            }
            conn.disconnect()
        } catch (_: Exception) {}
        Pair(null, 0L)
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingStatus = true) }
            try {
                val (freshBody, freshRtt) = fetchFreshTunnelStatus()
                val body = freshBody ?: ApiClient.apiService.getStatus().body()
                if (body != null) {
                    val isConnected = _uiState.value.connectionState == ConnectionState.CONNECTED

                    if (!isConnected && !NitroWarpVpnService.isRunning) {
                        val realIp = body.connectedIp ?: body.ip ?: ""
                        val realCode = body.countryCode?.takeIf { it.isNotBlank() && it != "XX" } ?: "BD"
                        if (realIp.isNotBlank()) {
                            _uiState.update { it.copy(realUserIp = realIp, realUserCountryCode = realCode) }
                        }
                    }

                    _uiState.update {
                        it.copy(
                            status = body,
                            measuredPing = if (freshRtt > 0L) freshRtt else it.measuredPing,
                            isRefreshingStatus = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isRefreshingStatus = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshingStatus = false) }
            }
        }
    }

    fun loadServers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true) }
            try {
                val response = ApiClient.apiService.getServers()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val rawList = body.servers ?: emptyList()

                    val parsedServers = mutableListOf<VpnServer>()
                    rawList.forEachIndexed { index, dto ->
                        val ip = dto.ip?.trim()
                        if (ip.isNullOrEmpty()) return@forEachIndexed

                        val ping = (dto.ping ?: 40L).coerceAtLeast(1L)
                        val countryShort = dto.countryShort?.trim()?.uppercase() ?: "XX"
                        val uniqueKey = "$countryShort:$ip"

                        val wasApproved = approvedServerKeys.contains(uniqueKey)
                        if (wasApproved) {
                            if (ping >= 500L) {
                                approvedServerKeys.remove(uniqueKey)
                                return@forEachIndexed
                            }
                        } else {
                            if (ping > 399L) {
                                return@forEachIndexed
                            }
                        }
                        approvedServerKeys.add(uniqueKey)

                        val (proto, port, cipher) = CryptoUtils.parseOvpnConfig(dto.ovpnConfigBase64)
                        val speedMbps = dto.speedMbps ?: ((dto.speed ?: 0L).toDouble() / 1_000_000.0)
                        val cleanCountryLong = dto.countryLong?.trim()?.takeIf { it.isNotEmpty() } ?: countryShort
                        val defaultLabel = "$cleanCountryLong • Location #1"

                        parsedServers.add(
                            VpnServer(
                                id = dto.id ?: "server_${countryShort}_${ip}_$index",
                                hostName = dto.hostName?.takeIf { it.isNotBlank() } ?: defaultLabel,
                                ip = ip,
                                ping = ping,
                                speedMbps = (speedMbps * 100.0).roundToInt() / 100.0,
                                countryLong = cleanCountryLong,
                                countryShort = countryShort,
                                flagEmoji = CryptoUtils.countryCodeToFlagEmoji(countryShort),
                                protocol = proto,
                                port = port,
                                cipher = cipher,
                                sessions = dto.sessions ?: dto.numVpnSessions ?: 10L,
                                ovpnConfigBase64 = dto.ovpnConfigBase64 ?: "",
                                locationLabel = defaultLabel
                            )
                        )
                    }

                    val groupedAz = buildCountryGroupsAz(parsedServers, _uiState.value.sortOption)

                    val currentState = _uiState.value
                    val finalGroups = if (currentState.connectionState == ConnectionState.CONNECTED &&
                        currentState.activeEngine == VpnEngine.GLOBAL &&
                        currentState.selectedServer != null
                    ) {
                        val activeServer = currentState.selectedServer!!
                        val existsInGroups = groupedAz.any {
                            it.countryShort.equals(activeServer.countryShort, ignoreCase = true)
                        }
                        if (!existsInGroups) {
                            val pinnedServer = activeServer.copy(
                                locationLabel = "${activeServer.countryLong} • Location #1"
                            )
                            val pinnedGroup = CountryGroup(
                                countryShort = activeServer.countryShort.uppercase(),
                                countryLong = activeServer.countryLong,
                                flagEmoji = activeServer.flagEmoji,
                                bestPing = activeServer.ping,
                                maxSpeedMbps = activeServer.speedMbps,
                                servers = listOf(pinnedServer),
                                isFavorite = currentState.favoriteCountries.contains(activeServer.countryShort.uppercase()),
                                isPinnedConnected = true
                            )
                            (groupedAz + pinnedGroup).sortedBy { it.countryLong.lowercase() }
                        } else {
                            groupedAz.map { group ->
                                if (group.countryShort.equals(activeServer.countryShort, ignoreCase = true) &&
                                    group.servers.none { it.ip == activeServer.ip }
                                ) {
                                    val updatedServers = (listOf(activeServer) + group.servers)
                                        .take(3)
                                        .mapIndexed { idx, srv ->
                                            srv.copy(locationLabel = "${srv.countryLong} • Location #${idx + 1}")
                                        }
                                    group.copy(servers = updatedServers, isPinnedConnected = true)
                                } else {
                                    group
                                }
                            }
                        }
                    } else {
                        groupedAz
                    }

                    val flattenedServers = finalGroups.flatMap { it.servers }

                    _uiState.update { current ->
                        current.copy(
                            servers = flattenedServers,
                            countryGroups = finalGroups,
                            selectedServer = current.selectedServer ?: flattenedServers.firstOrNull(),
                            isLoadingServers = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingServers = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingServers = false,
                        toastMessage = "Unable to load servers: ${e.message ?: "Check connection"}"
                    )
                }
            }
        }
    }

    private fun buildCountryGroupsAz(
        servers: List<VpnServer>,
        sortOption: SortOption
    ): List<CountryGroup> {
        val favSet = _uiState.value.favoriteCountries
        return servers
            .groupBy { it.countryShort.uppercase() }
            .map { (code, countryServers) ->
                val sortedIps = when (sortOption) {
                    SortOption.PING -> countryServers.sortedWith(
                        compareBy<VpnServer> { it.ping }.thenByDescending { it.speedMbps }
                    )
                    SortOption.SPEED -> countryServers.sortedWith(
                        compareByDescending<VpnServer> { it.speedMbps }.thenBy { it.ping }
                    )
                }
                    .take(3)
                    .mapIndexed { idx, srv ->
                        srv.copy(locationLabel = "${srv.countryLong} • Location #${idx + 1}")
                    }

                val first = sortedIps.first()
                CountryGroup(
                    countryShort = code,
                    countryLong = first.countryLong,
                    flagEmoji = first.flagEmoji,
                    bestPing = sortedIps.minOfOrNull { it.ping } ?: first.ping,
                    maxSpeedMbps = sortedIps.maxOfOrNull { it.speedMbps } ?: first.speedMbps,
                    servers = sortedIps,
                    isFavorite = favSet.contains(code),
                    isPinnedConnected = false
                )
            }
            .sortedBy { it.countryLong.lowercase() }
    }

    fun toggleFavoriteCountry(countryShort: String) {
        val code = countryShort.uppercase()
        _uiState.update { current ->
            val updated = current.favoriteCountries.toMutableSet()
            if (updated.contains(code)) {
                updated.remove(code)
            } else {
                updated.add(code)
            }
            try {
                val prefs = getApplication<Application>().getSharedPreferences("nitrowarp_favorites_prefs", Context.MODE_PRIVATE)
                prefs.edit().putStringSet("favorite_countries", updated).apply()
            } catch (_: Exception) {}

            val updatedGroups = current.countryGroups.map { group ->
                group.copy(isFavorite = updated.contains(group.countryShort.uppercase()))
            }
            current.copy(
                favoriteCountries = updated,
                countryGroups = updatedGroups
            )
        }
    }

    fun toggleFavoritesSection() {
        _uiState.update { it.copy(isFavoritesSectionExpanded = !it.isFavoritesSectionExpanded) }
    }

    fun toggleCountryExpanded(countryShort: String) {
        val code = countryShort.uppercase()
        _uiState.update { current ->
            val updated = current.expandedCountries.toMutableSet()
            if (updated.contains(code)) {
                updated.remove(code)
            } else {
                updated.add(code)
            }
            current.copy(expandedCountries = updated)
        }
    }

    fun setAllCountriesExpanded(expandAll: Boolean) {
        _uiState.update { current ->
            val updated = if (expandAll) {
                current.countryGroups.map { it.countryShort }.toSet()
            } else {
                emptySet()
            }
            current.copy(expandedCountries = updated)
        }
    }

    fun switchEngine(
        newEngine: VpnEngine,
        onStartTunnel: (config: TunnelConfig) -> Unit,
        onStopTunnel: () -> Unit
    ) {
        if (_uiState.value.activeEngine == newEngine) return
        _uiState.update { it.copy(activeEngine = newEngine) }

        if (_uiState.value.connectionState != ConnectionState.DISCONNECTED) {
            disconnect(onStopTunnel)
        }
    }

    fun toggleConnect(
        onStartTunnel: (config: TunnelConfig) -> Unit,
        onStopTunnel: () -> Unit
    ) {
        when (_uiState.value.connectionState) {
            ConnectionState.CONNECTED -> disconnect(onStopTunnel)
            ConnectionState.DISCONNECTED -> connect(onStartTunnel, onStopTunnel)
            ConnectionState.HANDSHAKING -> disconnect(onStopTunnel)
        }
    }

    fun selectServerAndConnect(
        server: VpnServer,
        onStartTunnel: (config: TunnelConfig) -> Unit,
        onStopTunnel: () -> Unit
    ) {
        _uiState.update {
            it.copy(
                selectedServer = server,
                activeEngine = VpnEngine.GLOBAL,
                isSheetOpen = false,
                toastMessage = "Connecting to ${server.flagEmoji} ${server.locationLabel}"
            )
        }
        connect(onStartTunnel, onStopTunnel)
    }

    private fun connect(
        onStartTunnel: (config: TunnelConfig) -> Unit,
        onStopTunnel: () -> Unit
    ) {
        viewModelScope.launch {
            if (NitroWarpVpnService.isRunning) {
                onStopTunnel()
                delay(400)
            }

            _uiState.update {
                it.copy(
                    connectionState = ConnectionState.HANDSHAKING,
                    downloadSpeed = 0.0,
                    uploadSpeed = 0.0
                )
            }

            try {
                val isTurboMode = _uiState.value.activeEngine == VpnEngine.TURBO

                if (isTurboMode) {
                    val (priv, pub) = WireGuardTunnelManager.generateKeyPair()
                    _uiState.update { it.copy(handshakeStepText = "Generating WARP Keys...") }

                    val warpResponse = try {
                        ApiClient.apiService.generateWarp(WarpGenerateRequest(publicKey = pub)).body()
                    } catch (_: Exception) {
                        null
                    }

                    val clientAddresses = warpResponse?.connection?.clientAddresses ?: "172.16.0.2/32"
                    val clientIp = clientAddresses.split(",")[0].trim().substringBefore("/")
                    val serverPub = warpResponse?.connection?.serverPublicKey
                        ?: "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="
                    val endpoint = warpResponse?.connection?.endpointV4 ?: "162.159.192.1:2408"

                    _uiState.update { it.copy(handshakeStepText = "Establishing WARP Tunnel...") }
                    onStartTunnel(
                        TunnelConfig(
                            sessionName = "Cloudflare WARP Turbo",
                            engine = "TURBO",
                            clientIp = clientIp,
                            prefixLength = 32,
                            mtu = 1280,
                            privateKeyB64 = priv,
                            serverPublicKeyB64 = serverPub,
                            endpoint = endpoint
                        )
                    )
                } else {
                    val server = _uiState.value.selectedServer
                        ?: _uiState.value.servers.firstOrNull()

                    if (server == null || server.ip.isBlank()) {
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.DISCONNECTED,
                                handshakeStepText = "",
                                toastMessage = "Selected Global IP not available. Stayed disconnected."
                            )
                        }
                        onStopTunnel()
                        return@launch
                    }

                    _uiState.update {
                        it.copy(handshakeStepText = "Starting OpenVPN (${server.countryLong})...")
                    }

                    onStartTunnel(
                        TunnelConfig(
                            sessionName = "${server.countryLong} (${server.ip})",
                            engine = "GLOBAL",
                            clientIp = "",
                            prefixLength = 32,
                            mtu = 1400,
                            privateKeyB64 = "",
                            serverPublicKeyB64 = "",
                            endpoint = "${server.ip}:${server.port}",
                            serverIp = server.ip,
                            serverPort = server.port,
                            protocol = server.protocol,
                            cipher = server.cipher,
                            countryLong = server.countryLong,
                            countryShort = server.countryShort,
                            ovpnConfigBase64 = server.ovpnConfigBase64
                        )
                    )
                }

                var isTunnelActive = false
                val maxWaitMs = 24000L
                val pollIntervalMs = 250L
                var elapsed = 0L

                while (elapsed < maxWaitMs) {
                    if (NitroWarpVpnService.isRunning && NitroWarpVpnService.activeTunnelHandle >= 0) {
                        isTunnelActive = true
                        break
                    }
                    if (!isTurboMode) {
                        val ovpnStep = NitroWarpVpnService.currentOpenVpnState
                        if (ovpnStep.isNotBlank() && ovpnStep != "DISCONNECTED") {
                            _uiState.update {
                                it.copy(handshakeStepText = "OpenVPN: $ovpnStep...")
                            }
                        }
                    }
                    if (!NitroWarpVpnService.lastErrorMessage.isNullOrBlank() && elapsed > 1200L) {
                        break
                    }
                    delay(pollIntervalMs)
                    elapsed += pollIntervalMs
                }

                if (!isTunnelActive) {
                    val err = NitroWarpVpnService.lastErrorMessage
                        ?: if (isTurboMode) {
                            "Cloudflare WARP connection timed out."
                        } else {
                            "Global server unreachable. Did NOT connect to any fallback IP."
                        }
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.DISCONNECTED,
                            handshakeStepText = "",
                            toastMessage = err
                        )
                    }
                    onStopTunnel()
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED,
                        handshakeStepText = ""
                    )
                }
                startSessionTimer()
                startRealTelemetry()
                delay(1500)
                refreshStatus()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        handshakeStepText = "",
                        toastMessage = "Connection error: ${e.message}"
                    )
                }
                onStopTunnel()
            }
        }
    }

    fun disconnect(onStopTunnel: () -> Unit) {
        viewModelScope.launch {
            onStopTunnel()
            stopSessionTimer()
            stopTelemetry()
            _uiState.update { current ->
                val restoredStatus = if (current.realUserIp.isNotBlank()) {
                    current.status?.copy(
                        ip = current.realUserIp,
                        connectedIp = current.realUserIp,
                        countryCode = current.realUserCountryCode
                    ) ?: StatusResponse(
                        ip = current.realUserIp,
                        connectedIp = current.realUserIp,
                        countryCode = current.realUserCountryCode
                    )
                } else {
                    current.status
                }

                val cleanedGroups = current.countryGroups.filterNot { it.isPinnedConnected }
                val cleanedServers = cleanedGroups.flatMap { it.servers }

                current.copy(
                    status = restoredStatus,
                    connectionState = ConnectionState.DISCONNECTED,
                    handshakeStepText = "",
                    downloadSpeed = 0.0,
                    uploadSpeed = 0.0,
                    sessionSeconds = 0,
                    countryGroups = cleanedGroups,
                    servers = cleanedServers
                )
            }
            delay(400)
            refreshStatus()
            loadServers()
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        _uiState.update { it.copy(sessionSeconds = 0) }
        sessionTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(sessionSeconds = it.sessionSeconds + 1) }
            }
        }
    }

    private fun stopSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = null
    }

    private fun startRealTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            var lastWgRx = NitroWarpVpnService.getActiveTrafficStats()?.rxBytes ?: 0L
            var lastWgTx = NitroWarpVpnService.getActiveTrafficStats()?.txBytes ?: 0L
            var lastSysRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            var lastSysTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            var lastTime = System.currentTimeMillis()

            while (isActive && _uiState.value.connectionState == ConnectionState.CONNECTED) {
                delay(1000)
                val now = System.currentTimeMillis()
                val dtSeconds = ((now - lastTime) / 1000.0).coerceAtLeast(0.5)
                lastTime = now

                val wgStats = NitroWarpVpnService.getActiveTrafficStats()
                val currWgRx = wgStats?.rxBytes ?: 0L
                val currWgTx = wgStats?.txBytes ?: 0L

                val currSysRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
                val currSysTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)

                val deltaRxBytes = if (currWgRx > lastWgRx) {
                    currWgRx - lastWgRx
                } else {
                    (currSysRx - lastSysRx).coerceAtLeast(0L)
                }

                val deltaTxBytes = if (currWgTx > lastWgTx) {
                    currWgTx - lastWgTx
                } else {
                    (currSysTx - lastSysTx).coerceAtLeast(0L)
                }

                lastWgRx = currWgRx
                lastWgTx = currWgTx
                lastSysRx = currSysRx
                lastSysTx = currSysTx

                val realMbpsDown = (deltaRxBytes * 8.0) / (1_000_000.0 * dtSeconds)
                val realMbpsUp = (deltaTxBytes * 8.0) / (1_000_000.0 * dtSeconds)

                _uiState.update {
                    it.copy(
                        downloadSpeed = (realMbpsDown * 100.0).roundToInt() / 100.0,
                        uploadSpeed = (realMbpsUp * 100.0).roundToInt() / 100.0
                    )
                }
            }
        }
    }

    private fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        _uiState.update {
            it.copy(
                downloadSpeed = 0.0,
                uploadSpeed = 0.0
            )
        }
    }

    fun setSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSheetOpen = isOpen) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setCountryFilter(country: String) {
        _uiState.update { it.copy(selectedCountryFilter = country) }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { current ->
            val regrouped = buildCountryGroupsAz(current.servers, sort)
            current.copy(
                sortOption = sort,
                countryGroups = regrouped,
                servers = regrouped.flatMap { it.servers }
            )
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
