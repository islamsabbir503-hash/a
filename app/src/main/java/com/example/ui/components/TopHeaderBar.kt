package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionState
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGlassSurface
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.VpnUiState

@Composable
fun TopHeaderBar(
    state: VpnUiState,
    onRefresh: () -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    // Proportional scaling for 5.0" to 6.7"+ displays
    val horizontalPad = (screenWidth * 0.045f).coerceIn(12.dp, 22.dp)
    val verticalPad = (screenHeight * 0.01f).coerceIn(5.dp, 12.dp)
    val logoBoxSize = (screenWidth * 0.102f).coerceIn(34.dp, 44.dp)
    val logoIconSize = (logoBoxSize * 0.56f).coerceIn(19.dp, 25.dp)
    val titleFontSize = (screenWidth.value * 0.045f).coerceIn(15.5f, 20f).sp
    val subtitleFontSize = (screenWidth.value * 0.025f).coerceIn(9f, 11.5f).sp

    val shieldAccent = when (state.connectionState) {
        ConnectionState.CONNECTED -> EmeraldNeon
        ConnectionState.HANDSHAKING -> CyberCyan
        ConnectionState.DISCONNECTED -> CrimsonCoral
    }

    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (state.isRefreshingStatus) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = verticalPad),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Brand Emblem & Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                modifier = Modifier.size(logoBoxSize),
                shape = RoundedCornerShape(12.dp),
                color = CyberGlassSurface,
                border = BorderStroke(1.dp, shieldAccent.copy(alpha = 0.45f))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(shieldAccent.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "NitroWarp Shield",
                        tint = shieldAccent,
                        modifier = Modifier.size(logoIconSize)
                    )
                }
            }

            Spacer(modifier = Modifier.width((screenWidth * 0.03f).coerceIn(10.dp, 14.dp)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NitroWarp VPN",
                    color = TextPrimary,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "DUAL-ENGINE HYBRID",
                    color = Color(0xFF94A3B8),
                    fontSize = subtitleFontSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right Compact Telemetry Refresh Button
        Surface(
            modifier = Modifier
                .size(logoBoxSize)
                .clip(CircleShape)
                .clickable { onRefresh() },
            shape = CircleShape,
            color = CyberGlassSurface,
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Telemetry",
                    tint = if (state.isRefreshingStatus) CyberCyan else Color(0xFF94A3B8),
                    modifier = Modifier
                        .size((logoBoxSize * 0.5f).coerceIn(18.dp, 22.dp))
                        .rotate(if (state.isRefreshingStatus) rotation else 0f)
                )
            }
        }
    }
}
