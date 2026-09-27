package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DeepObsidian = Color(0xFF060911)
val CyberGlassSurface = Color(0xFF0F1626)
val CyberGlassCard = Color(0xD60F1626) // rgba(15, 22, 38, 0.84)
val CyberGlassBorder = Color(0x14FFFFFF) // rgba(255, 255, 255, 0.08)

val EmeraldNeon = Color(0xFF00F5A0)
val CyberCyan = Color(0xFF00D9F5)
val ElectricIndigo = Color(0xFF6366F1)
val ElectricPurple = Color(0xFF8B5CF6)
val CrimsonCoral = Color(0xFFF43F5E)
val CyberAmber = Color(0xFFF59E0B)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val TurboGradientBrush = Brush.linearGradient(
    colors = listOf(EmeraldNeon, CyberCyan)
)

val GlobalGradientBrush = Brush.linearGradient(
    colors = listOf(ElectricIndigo, ElectricPurple)
)

val DisconnectedPulseBrush = Brush.radialGradient(
    colors = listOf(CrimsonCoral.copy(alpha = 0.18f), Color.Transparent),
    radius = 500f
)

val HandshakingPulseBrush = Brush.radialGradient(
    colors = listOf(CyberCyan.copy(alpha = 0.22f), Color.Transparent),
    radius = 500f
)

val ConnectedPulseBrush = Brush.radialGradient(
    colors = listOf(EmeraldNeon.copy(alpha = 0.25f), Color.Transparent),
    radius = 500f
)
