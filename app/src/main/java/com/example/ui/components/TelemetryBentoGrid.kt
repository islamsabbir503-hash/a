package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGlassSurface
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.VpnUiState
import java.util.Locale

@Composable
fun TelemetryBentoGrid(
    state: VpnUiState
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val screenHeight = config.screenHeightDp.dp

    val horizontalPad = (screenWidth * 0.045f).coerceIn(12.dp, 22.dp)
    val cardSpacing = (screenWidth * 0.024f).coerceIn(7.dp, 12.dp)
    val cardInnerPad = (screenWidth * 0.03f).coerceIn(9.dp, 14.dp)
    val verticalGap = (screenHeight * 0.008f).coerceIn(4.dp, 8.dp)

    val labelFontSize = (screenWidth.value * 0.028f).coerceIn(9.5f, 12f).sp
    val valueFontSize = (screenWidth.value * 0.05f).coerceIn(16f, 21f).sp
    val unitFontSize = (screenWidth.value * 0.027f).coerceIn(9.5f, 11.5f).sp

    val pingValue = state.measuredPing
    val pingColor = when {
        pingValue <= 0L -> Color(0xFF94A3B8)
        pingValue < 80L -> EmeraldNeon
        pingValue < 180L -> Color(0xFFF59E0B)
        else -> CrimsonCoral
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = horizontalPad,
                vertical = (screenHeight * 0.005f).coerceIn(3.dp, 7.dp)
            )
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(cardSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Card 1: DOWNLOAD
        EqualTelemetryCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label = "DOWN",
            indicatorText = "↓",
            indicatorColor = EmeraldNeon,
            valueText = String.format(Locale.US, "%.2f", state.downloadSpeed),
            valueColor = TextPrimary,
            unitText = "Mbps",
            barColor = EmeraldNeon,
            barFraction = (state.downloadSpeed.toFloat() / 100f).coerceIn(0.12f, 1f),
            innerPadding = cardInnerPad,
            verticalGap = verticalGap,
            labelSize = labelFontSize,
            valueSize = valueFontSize,
            unitSize = unitFontSize
        )

        // Card 2: UPLOAD
        EqualTelemetryCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label = "UP",
            indicatorText = "↑",
            indicatorColor = CyberCyan,
            valueText = String.format(Locale.US, "%.2f", state.uploadSpeed),
            valueColor = TextPrimary,
            unitText = "Mbps",
            barColor = CyberCyan,
            barFraction = (state.uploadSpeed.toFloat() / 50f).coerceIn(0.12f, 1f),
            innerPadding = cardInnerPad,
            verticalGap = verticalGap,
            labelSize = labelFontSize,
            valueSize = valueFontSize,
            unitSize = unitFontSize
        )

        // Card 3: PING (100% Identical height & structure to DOWN and UP)
        EqualTelemetryCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label = "PING",
            indicatorText = "●",
            indicatorColor = pingColor,
            valueText = if (pingValue > 0) "$pingValue" else "--",
            valueColor = pingColor,
            unitText = "ms",
            barColor = pingColor,
            barFraction = if (pingValue > 0) (1f - (pingValue.toFloat() / 500f)).coerceIn(0.18f, 1f) else 0.15f,
            innerPadding = cardInnerPad,
            verticalGap = verticalGap,
            labelSize = labelFontSize,
            valueSize = valueFontSize,
            unitSize = unitFontSize
        )
    }
}

@Composable
private fun EqualTelemetryCard(
    modifier: Modifier,
    label: String,
    indicatorText: String,
    indicatorColor: Color,
    valueText: String,
    valueColor: Color,
    unitText: String,
    barColor: Color,
    barFraction: Float,
    innerPadding: androidx.compose.ui.unit.Dp,
    verticalGap: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueSize: androidx.compose.ui.unit.TextUnit,
    unitSize: androidx.compose.ui.unit.TextUnit
) {
    // Auto-adjust font size if number has 6+ characters so it never clips on 5-inch screens
    val adaptiveValueSize = if (valueText.length >= 6) valueSize * 0.88f else valueSize

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberGlassSurface),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(innerPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Label + Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color(0xFF94A3B8),
                    fontSize = labelSize,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = indicatorText,
                    color = indicatorColor,
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(verticalGap))

            // Middle Row: Numeric Value
            Text(
                text = valueText,
                color = valueColor,
                fontSize = adaptiveValueSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )

            Spacer(modifier = Modifier.height((verticalGap * 0.6f).coerceAtLeast(3.dp)))

            // Bottom Row: Unit + Accent Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = unitText,
                    color = Color(0xFF64748B),
                    fontSize = unitSize,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height((verticalGap * 0.7f).coerceAtLeast(4.dp)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
            }
        }
    }
}
