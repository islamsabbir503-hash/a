package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusResponse(
    @Json(name = "ip") val ip: String? = null,
    @Json(name = "country_code") val countryCode: String? = null,
    @Json(name = "city") val city: String? = null,
    @Json(name = "region") val region: String? = null,
    @Json(name = "colo") val colo: String? = null,
    @Json(name = "asn") val asn: Long? = null,
    @Json(name = "isp") val isp: String? = null,
    @Json(name = "asOrganization") val asOrganization: String? = null,
    @Json(name = "connected_ip") val connectedIp: String? = null,
    @Json(name = "isCloudflare") val isCloudflare: Boolean? = null,
    @Json(name = "is_cloudflare") val isCloudflareSnake: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ServersResponse(
    @Json(name = "entry_ping_threshold_ms") val entryPingThresholdMs: Int? = 399,
    @Json(name = "dropout_ping_threshold_ms") val dropoutPingThresholdMs: Int? = 500,
    @Json(name = "max_countries_limit") val maxCountriesLimit: Int? = 30,
    @Json(name = "max_ips_per_country") val maxIpsPerCountry: Int? = 3,
    @Json(name = "total_countries") val totalCountries: Int? = 0,
    @Json(name = "count") val count: Int? = 0,
    @Json(name = "servers") val servers: List<ServerDto>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ServerDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "hostName") val hostName: String? = null,
    @Json(name = "ip") val ip: String? = null,
    @Json(name = "ping") val ping: Long? = null,
    @Json(name = "speedMbps") val speedMbps: Double? = null,
    @Json(name = "speed") val speed: Long? = null,
    @Json(name = "countryLong") val countryLong: String? = null,
    @Json(name = "countryShort") val countryShort: String? = null,
    @Json(name = "sessions") val sessions: Long? = null,
    @Json(name = "numVpnSessions") val numVpnSessions: Long? = null,
    @Json(name = "ovpnConfigBase64") val ovpnConfigBase64: String? = null
)

data class VpnServer(
    val id: String,
    val hostName: String,
    val ip: String,
    val ping: Long,
    val speedMbps: Double,
    val countryLong: String,
    val countryShort: String,
    val flagEmoji: String,
    val protocol: String,
    val port: Int,
    val cipher: String,
    val sessions: Long,
    val ovpnConfigBase64: String,
    val locationLabel: String = ""
)

data class CountryGroup(
    val countryShort: String,
    val countryLong: String,
    val flagEmoji: String,
    val bestPing: Long,
    val maxSpeedMbps: Double,
    val servers: List<VpnServer>,
    val isFavorite: Boolean = false,
    val isPinnedConnected: Boolean = false
)

@JsonClass(generateAdapter = true)
data class WarpGenerateRequest(
    @Json(name = "publicKey") val publicKey: String
)

@JsonClass(generateAdapter = true)
data class WarpConnectionDto(
    @Json(name = "serverPublicKey") val serverPublicKey: String? = null,
    @Json(name = "endpointV4") val endpointV4: String? = null,
    @Json(name = "endpointV6") val endpointV6: String? = null,
    @Json(name = "engageEndpoint") val engageEndpoint: String? = null,
    @Json(name = "clientAddresses") val clientAddresses: String? = null,
    @Json(name = "dns") val dns: String? = null
)

@JsonClass(generateAdapter = true)
data class WarpGenerateResponse(
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "connection") val connection: WarpConnectionDto? = null,
    @Json(name = "wireguardConfigText") val wireguardConfigText: String? = null,
    @Json(name = "clientAddress") val clientAddress: String? = null,
    @Json(name = "endpoint") val endpoint: String? = null
)

data class TunnelConfig(
    val sessionName: String,
    val engine: String, // "TURBO" or "GLOBAL"
    val clientIp: String = "172.16.0.2",
    val prefixLength: Int = 32,
    val mtu: Int = 1280,
    val privateKeyB64: String = "",
    val serverPublicKeyB64: String = "",
    val endpoint: String = "162.159.192.1:2408",
    val serverIp: String = "",
    val serverPort: Int = 443,
    val protocol: String = "TCP",
    val cipher: String = "AES-128-CBC",
    val countryLong: String = "",
    val countryShort: String = "",
    val ovpnConfigBase64: String = ""
)

enum class VpnEngine {
    TURBO,
    GLOBAL
}

enum class ConnectionState {
    DISCONNECTED,
    HANDSHAKING,
    CONNECTED
}

enum class SortOption {
    SPEED,
    PING
}
