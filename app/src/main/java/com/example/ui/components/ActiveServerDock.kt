package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionState
import com.example.data.model.VpnEngine
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGlassSurface
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.VpnUiState

@Composable
fun ActiveServerDock(
    state: VpnUiState,
    onOpenServerSheet: () -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    val horizontalPad = (screenWidth * 0.045f).coerceIn(12.dp, 22.dp)
    val bottomPad = (screenHeight * 0.012f).coerceIn(6.dp, 14.dp)
    val innerHorizontalPad = (screenWidth * 0.035f).coerceIn(11.dp, 16.dp)
    val innerVerticalPad = (screenHeight * 0.013f).coerceIn(9.dp, 13.dp)

    val iconBoxSize = (screenWidth * 0.108f).coerceIn(36.dp, 46.dp)
    val titleSize = (screenWidth.value * 0.037f).coerceIn(13f, 16f).sp
    val subSize = (screenWidth.value * 0.028f).coerceIn(10f, 12f).sp
    val badgeFontSize = (screenWidth.value * 0.028f).coerceIn(10f, 12f).sp

    val isTurbo = state.activeEngine == VpnEngine.TURBO
    val isConnected = state.connectionState == ConnectionState.CONNECTED
    val selectedServer = state.selectedServer

    val cardBorderColor = when {
        isConnected -> EmeraldNeon.copy(alpha = 0.65f)
        !isTurbo -> CyberCyan.copy(alpha = 0.4f)
        else -> Color(0xFF1E293B)
    }

    val dockTitle = if (isTurbo) {
        "Cloudflare WARP Anycast"
    } else {
        selectedServer?.locationLabel?.takeIf { it.isNotBlank() }
            ?: selectedServer?.countryLong
            ?: "Select Global Relay"
    }

    val dockSubtitle = if (isTurbo) {
        val colo = state.status?.colo?.takeIf { it.isNotBlank() } ?: "Auto"
        "Anycast 1.1.1.1 • $colo Edge Relay"
    } else {
        selectedServer?.let {
            "⚡ ${it.speedMbps.toInt()} Mbps • ${it.protocol}:${it.port} • ${it.cipher}"
        } ?: "${state.countryGroups.size} Countries • Tap to choose location"
    }

    val pingToShow = if (isTurbo) {
        state.measuredPing
    } else {
        selectedServer?.ping ?: state.measuredPing
    }

    val pingColor = when {
        pingToShow <= 0L -> EmeraldNeon
        pingToShow < 80L -> EmeraldNeon
        pingToShow < 180L -> Color(0xFFF59E0B)
        else -> CrimsonCoral
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(start = horizontalPad, end = horizontalPad, bottom = bottomPad, top = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenServerSheet() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CyberGlassSurface),
        border = BorderStroke(1.2.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = innerHorizontalPad, vertical = innerVerticalPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Icon / Country Flag + Node Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    modifier = Modifier.size(iconBoxSize),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTurbo) EmeraldNeon.copy(alpha = 0.14f) else CyberCyan.copy(alpha = 0.14f),
                    border = BorderStroke(
                        1.dp,
                        if (isTurbo) EmeraldNeon.copy(alpha = 0.4f) else CyberCyan.copy(alpha = 0.4f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isTurbo) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Cloudflare Turbo",
                                tint = EmeraldNeon,
                                modifier = Modifier.size((iconBoxSize * 0.52f).coerceIn(18.dp, 24.dp))
                            )
                        } else {
                            Text(
                                text = selectedServer?.flagEmoji ?: "🌍",
                                fontSize = (screenWidth.value * 0.048f).coerceIn(17f, 21f).sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width((screenWidth * 0.028f).coerceIn(9.dp, 13.dp)))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dockTitle,
                        color = TextPrimary,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = dockSubtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = subSize,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Single-Line Badge + Up Arrow (Never wraps vertically on 5.0" or 6.7" screens)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                val badgeLabel = when {
                    pingToShow > 0L -> "$pingToShow ms"
                    isTurbo -> "Auto Edge"
                    else -> "Select"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = pingColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, pingColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = badgeLabel,
                        color = pingColor,
                        fontSize = badgeFontSize,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Open Server List",
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
