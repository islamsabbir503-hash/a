package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.crypto.CryptoUtils
import com.example.data.model.ConnectionState
import com.example.data.model.VpnEngine
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGlassSurface
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.VpnUiState
import java.util.Locale

@Composable
fun HeroConnectCore(
    state: VpnUiState,
    onConnectToggle: () -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    val isConnected = state.connectionState == ConnectionState.CONNECTED
    val isHandshaking = state.connectionState == ConnectionState.HANDSHAKING

    val primaryAccent = when (state.connectionState) {
        ConnectionState.CONNECTED -> EmeraldNeon
        ConnectionState.HANDSHAKING -> CyberCyan
        ConnectionState.DISCONNECTED -> CrimsonCoral
    }

    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isHandshaking) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isHandshaking) 750 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val hours = state.sessionSeconds / 3600
    val minutes = (state.sessionSeconds % 3600) / 60
    val seconds = state.sessionSeconds % 60
    val formattedTimer = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    // True Egress Country & IP Resolution (Matches real network state & whoer.net)
    val liveStatusIp = state.status?.connectedIp?.takeIf { it.isNotBlank() }
        ?: state.status?.ip?.takeIf { it.isNotBlank() }

    val liveStatusCountry = state.status?.countryCode?.takeIf { it.isNotBlank() && it != "XX" }

    val activeCountryCode = when {
        isConnected && liveStatusCountry != null -> liveStatusCountry
        isConnected && state.activeEngine == VpnEngine.GLOBAL && state.selectedServer != null ->
            state.selectedServer.countryShort
        !isConnected && state.realUserCountryCode.isNotBlank() -> state.realUserCountryCode
        liveStatusCountry != null -> liveStatusCountry
        else -> "BD"
    }

    val liveFlagEmoji = CryptoUtils.countryCodeToFlagEmoji(activeCountryCode)

    val liveIpAddress = when {
        isConnected && liveStatusIp != null -> liveStatusIp
        isConnected && state.activeEngine == VpnEngine.GLOBAL && state.selectedServer != null ->
            state.selectedServer.ip
        !isConnected && state.realUserIp.isNotBlank() -> state.realUserIp
        liveStatusIp != null -> liveStatusIp
        else -> "Detecting IP..."
    }

    // Strict Color Rule: Red before connect, Green when connected
    val ipPillAccentColor = if (isConnected) EmeraldNeon else CrimsonCoral

    // Protocol summary text
    val protocolBadgeText = if (state.activeEngine == VpnEngine.TURBO) {
        "WireGuard 1280 MTU • ChaCha20-Poly1305"
    } else {
        val srv = state.selectedServer
        if (srv != null) {
            "OpenVPN ${srv.protocol}:${srv.port} • ${srv.cipher}"
        } else {
            "OpenVPN Multi-Node • AES-128-CBC"
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = (screenWidth * 0.04f).coerceIn(12.dp, 20.dp)),
        contentAlignment = Alignment.Center
    ) {
        val availableHeight = maxHeight
        // Dynamically adapt orb and spacing to fit both compact 5.0" and tall 6.7"+ screens
        val outerOrbSize = (availableHeight * 0.48f).coerceIn(124.dp, (screenHeight * 0.225f).coerceIn(136.dp, 190.dp))
        val innerOrbSize = (outerOrbSize * 0.82f).coerceIn(102.dp, 156.dp)
        val coreIconSize = (innerOrbSize * 0.28f).coerceIn(28.dp, 42.dp)
        val statusTitleSize = (screenWidth.value * 0.037f).coerceIn(12.5f, 15.5f).sp
        val statusSubSize = (screenWidth.value * 0.025f).coerceIn(9f, 11f).sp
        val timerFontSize = (screenWidth.value * 0.068f).coerceIn(22f, 30f).sp
        val ipFontSize = (screenWidth.value * 0.036f).coerceIn(12.5f, 15f).sp
        val verticalGap = (availableHeight * 0.024f).coerceIn(4.dp, 10.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Interactive Circular Power Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(outerOrbSize)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.18f),
                                primaryAccent.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(1.dp, primaryAccent.copy(alpha = 0.28f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onConnectToggle
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(innerOrbSize)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) {
                                Brush.linearGradient(
                                    colors = listOf(EmeraldNeon, CyberCyan)
                                )
                            } else {
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF161F33),
                                        Color(0xFF0D1322)
                                    )
                                )
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = primaryAccent.copy(alpha = if (isConnected) 0.9f else 0.55f),
                            shape = CircleShape
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Shield else Icons.Default.PowerSettingsNew,
                            contentDescription = "Connect Toggle",
                            tint = if (isConnected) Color(0xFF060913) else primaryAccent,
                            modifier = Modifier.size(coreIconSize)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when (state.connectionState) {
                                ConnectionState.CONNECTED -> "PROTECTED"
                                ConnectionState.HANDSHAKING -> "CONNECTING"
                                ConnectionState.DISCONNECTED -> "DISCONNECTED"
                            },
                            color = if (isConnected) Color(0xFF060913) else primaryAccent,
                            fontSize = statusTitleSize,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            softWrap = false
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when (state.connectionState) {
                                ConnectionState.CONNECTED -> "TUNNEL ACTIVE"
                                ConnectionState.HANDSHAKING -> state.handshakeStepText.ifEmpty { "HANDSHAKING..." }
                                ConnectionState.DISCONNECTED -> "TAP TO CONNECT"
                            },
                            color = if (isConnected) Color(0xFF060913).copy(alpha = 0.78f) else Color(0xFF94A3B8),
                            fontSize = statusSubSize,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(verticalGap))

            // 2. Session Duration Timer
            Text(
                text = formattedTimer,
                color = if (isConnected) TextPrimary else Color(0xFF64748B),
                fontSize = timerFontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
                softWrap = false
            )

            Text(
                text = "SESSION DURATION",
                color = Color(0xFF64748B),
                fontSize = (screenWidth.value * 0.024f).coerceIn(8.5f, 10.5f).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                softWrap = false
            )

            Spacer(modifier = Modifier.height(verticalGap))

            // 3. Relocated Live Country Flag + IP Pill (Red before connect, Green when connected, NO "Unprotected" text)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ipPillAccentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.4.dp, ipPillAccentColor.copy(alpha = 0.85f))
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = (screenWidth * 0.04f).coerceIn(14.dp, 20.dp),
                        vertical = (screenHeight * 0.006f).coerceIn(4.dp, 7.dp)
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Country Flag inside Circular Ring (Red circle when disconnected, Green circle when connected)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size((screenWidth * 0.07f).coerceIn(24.dp, 30.dp))
                            .clip(CircleShape)
                            .background(ipPillAccentColor.copy(alpha = 0.18f))
                            .border(1.5.dp, ipPillAccentColor, CircleShape)
                    ) {
                        Text(
                            text = liveFlagEmoji,
                            fontSize = (screenWidth.value * 0.036f).coerceIn(12.5f, 15.5f).sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Only the Live IP Address (Red before connect, Green when connected)
                    Text(
                        text = liveIpAddress,
                        color = ipPillAccentColor,
                        fontSize = ipFontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height((verticalGap * 0.8f).coerceIn(4.dp, 8.dp)))

            // 4. Compact Protocol Encryption Badge
            Surface(
                shape = RoundedCornerShape(50),
                color = CyberGlassSurface,
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = protocolBadgeText,
                        color = Color(0xFF94A3B8),
                        fontSize = (screenWidth.value * 0.026f).coerceIn(9.5f, 11.5f).sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
