package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VpnEngine
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGlassSurface
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary

@Composable
fun EngineSelector(
    activeEngine: VpnEngine,
    onSelectEngine: (VpnEngine) -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    val horizontalPad = (screenWidth * 0.045f).coerceIn(12.dp, 22.dp)
    val selectorHeight = (screenHeight * 0.058f).coerceIn(42.dp, 52.dp)
    val tabFontSize = (screenWidth.value * 0.033f).coerceIn(11f, 14.5f).sp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalPad,
                vertical = (screenHeight * 0.005f).coerceIn(3.dp, 7.dp)
            )
            .height(selectorHeight),
        shape = RoundedCornerShape(16.dp),
        color = CyberGlassSurface,
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab 1: Cloudflare Turbo
            val isTurbo = activeEngine == VpnEngine.TURBO
            val turboBrush = if (isTurbo) {
                Brush.horizontalGradient(listOf(EmeraldNeon, CyberCyan))
            } else {
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(turboBrush)
                    .clickable { onSelectEngine(VpnEngine.TURBO) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡ Cloudflare Turbo",
                    color = if (isTurbo) Color(0xFF060913) else Color(0xFF94A3B8),
                    fontSize = tabFontSize,
                    fontWeight = if (isTurbo) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Tab 2: Global Multi-IP
            val isGlobal = activeEngine == VpnEngine.GLOBAL
            val globalBrush = if (isGlobal) {
                Brush.horizontalGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))
            } else {
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(globalBrush)
                    .clickable { onSelectEngine(VpnEngine.GLOBAL) }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🌍 Global Multi-IP",
                    color = if (isGlobal) TextPrimary else Color(0xFF94A3B8),
                    fontSize = tabFontSize,
                    fontWeight = if (isGlobal) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
