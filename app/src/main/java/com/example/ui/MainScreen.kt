package com.example.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ConnectionState
import com.example.data.model.TunnelConfig
import com.example.data.model.VpnEngine
import com.example.data.model.VpnServer
import com.example.ui.components.ActiveServerDock
import com.example.ui.components.EngineSelector
import com.example.ui.components.HeroConnectCore
import com.example.ui.components.ServerSelectorSheet
import com.example.ui.components.TelemetryBentoGrid
import com.example.ui.components.TopHeaderBar
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.VpnUiState
import com.example.ui.viewmodel.VpnViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: VpnViewModel,
    onStartTunnel: (config: TunnelConfig) -> Unit,
    onStopTunnel: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingServerToConnect by remember { mutableStateOf<VpnServer?>(null) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var activeBannerMessage by remember { mutableStateOf<String?>(null) }

    // Custom themed Cyber-Glass banner controller (Replaces all OS-default Toasts)
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            activeBannerMessage = msg
            viewModel.clearToast()
        }
    }

    LaunchedEffect(activeBannerMessage) {
        if (activeBannerMessage != null) {
            delay(3200L)
            activeBannerMessage = null
        }
    }

    // Android System VPN Permission Activity Launcher
    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val server = pendingServerToConnect
            if (server != null) {
                viewModel.selectServerAndConnect(server, onStartTunnel, onStopTunnel)
                pendingServerToConnect = null
            } else {
                viewModel.toggleConnect(onStartTunnel, onStopTunnel)
            }
        } else {
            pendingServerToConnect = null
            activeBannerMessage = "VPN permission denied. Tunnel cannot start."
        }
    }

    // Connect / Disconnect Intent Controller
    val handleConnectRequest = {
        when (state.connectionState) {
            ConnectionState.CONNECTED -> {
                showDisconnectDialog = true
            }
            ConnectionState.HANDSHAKING -> {
                viewModel.disconnect(onStopTunnel)
            }
            ConnectionState.DISCONNECTED -> {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPrepareLauncher.launch(prepareIntent)
                } else {
                    viewModel.toggleConnect(onStartTunnel, onStopTunnel)
                }
            }
        }
    }

    // Dynamic ambient radial lighting behind the Hero Core
    val ambientCenterColor = when (state.connectionState) {
        ConnectionState.CONNECTED -> EmeraldNeon.copy(alpha = 0.16f)
        ConnectionState.HANDSHAKING -> CyberCyan.copy(alpha = 0.15f)
        ConnectionState.DISCONNECTED -> CrimsonCoral.copy(alpha = 0.10f)
    }

    val ambientBrush = Brush.radialGradient(
        colors = listOf(ambientCenterColor, Color.Transparent),
        radius = 700f
    )

    Scaffold(
        containerColor = DeepObsidian,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepObsidian)
        ) {
            // Ambient Radial Light
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ambientBrush)
            )

            // Responsive Root Layout (Scales proportionally across 5.0" to 6.7"+ screens)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val viewportHeight = maxHeight

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = (viewportHeight * 0.008f).coerceIn(4.dp, 10.dp)),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Child 1: Top Header Bar
                    TopHeaderBar(
                        state = state,
                        onRefresh = { viewModel.refreshStatus() }
                    )

                    // Child 2: Engine Selector
                    EngineSelector(
                        activeEngine = state.activeEngine,
                        onSelectEngine = { newEngine ->
                            viewModel.switchEngine(newEngine, onStartTunnel, onStopTunnel)
                        }
                    )

                    // Child 3: Central Hero Connect Core
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        HeroConnectCore(
                            state = state,
                            onConnectToggle = handleConnectRequest
                        )
                    }

                    // Child 4: Telemetry Bento Grid
                    TelemetryBentoGrid(
                        state = state
                    )

                    // Child 5: Active Server Dock
                    ActiveServerDock(
                        state = state,
                        onOpenServerSheet = { viewModel.setSheetOpen(true) }
                    )
                }
            }

            // Top Floating Cyber-Glass Toast Banner
            AnimatedVisibility(
                visible = activeBannerMessage != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 14.dp, start = 16.dp, end = 16.dp)
            ) {
                activeBannerMessage?.let { message ->
                    CyberToastBanner(
                        message = message,
                        onDismiss = { activeBannerMessage = null }
                    )
                }
            }

            // Disconnect Confirmation Dialog
            if (showDisconnectDialog) {
                DisconnectConfirmDialog(
                    state = state,
                    onDismiss = { showDisconnectDialog = false },
                    onConfirmDisconnect = {
                        showDisconnectDialog = false
                        viewModel.disconnect(onStopTunnel)
                    }
                )
            }

            // Slide-Up Server Selector Bottom Sheet
            if (state.isSheetOpen) {
                ServerSelectorSheet(
                    state = state,
                    onDismiss = { viewModel.setSheetOpen(false) },
                    onRefreshServers = { viewModel.loadServers() },
                    onSearchChanged = { viewModel.setSearchQuery(it) },
                    onSortChanged = { viewModel.setSortOption(it) },
                    onToggleCountry = { viewModel.toggleCountryExpanded(it) },
                    onToggleFavorite = { viewModel.toggleFavoriteCountry(it) },
                    onToggleFavoritesSection = { viewModel.toggleFavoritesSection() },
                    onSelectServer = { server ->
                        pendingServerToConnect = server
                        val prepareIntent = VpnService.prepare(context)
                        if (prepareIntent != null) {
                            vpnPrepareLauncher.launch(prepareIntent)
                        } else {
                            viewModel.selectServerAndConnect(server, onStartTunnel, onStopTunnel)
                            pendingServerToConnect = null
                        }
                    }
                )
            }
        }
    }
}

/**
 * Custom Cyber-Glass Floating Toast Banner with Screen-Proportional Sizing & Neon Accent Matching
 */
@Composable
private fun CyberToastBanner(
    message: String,
    onDismiss: () -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val iconBoxSize = (screenWidth * 0.072f).coerceIn(26.dp, 32.dp)
    val fontSize = (screenWidth.value * 0.032f).coerceIn(11.5f, 13.5f).sp

    val isError = message.contains("error", ignoreCase = true) ||
        message.contains("denied", ignoreCase = true) ||
        message.contains("canceled", ignoreCase = true) ||
        message.contains("timed out", ignoreCase = true) ||
        message.contains("unable", ignoreCase = true) ||
        message.contains("disconnect", ignoreCase = true)

    val isSuccess = message.contains("connected", ignoreCase = true) ||
        message.contains("protected", ignoreCase = true) ||
        message.contains("active", ignoreCase = true) ||
        message.contains("secure", ignoreCase = true)

    val bannerAccent = when {
        isError -> CrimsonCoral
        isSuccess -> EmeraldNeon
        else -> CyberCyan
    }

    val icon = when {
        isError -> Icons.Default.Close
        isSuccess -> Icons.Default.CheckCircle
        else -> Icons.Default.Bolt
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismiss() },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E1628).copy(alpha = 0.96f),
        border = BorderStroke(1.3.dp, bannerAccent),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconBoxSize)
                    .clip(CircleShape)
                    .background(bannerAccent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = bannerAccent,
                    modifier = Modifier.size((iconBoxSize * 0.6f).coerceIn(15.dp, 19.dp))
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = message,
                color = TextPrimary,
                fontSize = fontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Custom Dark Cyber-Glass Disconnect Confirmation Modal (Screen-Proportional)
 */
@Composable
private fun DisconnectConfirmDialog(
    state: VpnUiState,
    onDismiss: () -> Unit,
    onConfirmDisconnect: () -> Unit
) {
    val config = LocalConfiguration.current
    val screenWidth = config.screenWidthDp.dp
    val badgeSize = (screenWidth * 0.12f).coerceIn(42.dp, 52.dp)
    val titleFontSize = (screenWidth.value * 0.044f).coerceIn(16f, 19f).sp
    val bodyFontSize = (screenWidth.value * 0.031f).coerceIn(11.5f, 13f).sp
    val buttonHeight = (screenWidth * 0.115f).coerceIn(42.dp, 48.dp)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0B101E),
            border = BorderStroke(1.5.dp, CrimsonCoral.copy(alpha = 0.65f)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding((screenWidth * 0.05f).coerceIn(16.dp, 22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Warning Power Icon Badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(CrimsonCoral.copy(alpha = 0.15f))
                        .border(1.dp, CrimsonCoral, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Warning",
                        tint = CrimsonCoral,
                        modifier = Modifier.size((badgeSize * 0.54f).coerceIn(22.dp, 28.dp))
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title Text
                Text(
                    text = "Terminate Encrypted Tunnel?",
                    color = TextPrimary,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Active Node Summary Box
                val activeNodeTitle = if (state.activeEngine == VpnEngine.TURBO) {
                    "⚡ Cloudflare WARP Turbo"
                } else {
                    "${state.selectedServer?.flagEmoji ?: "🌍"} ${state.selectedServer?.countryLong ?: "Global Node"}"
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131B2E),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activeNodeTitle,
                            color = CyberCyan,
                            fontSize = (screenWidth.value * 0.034f).coerceIn(12f, 14f).sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Disconnecting will immediately expose your real ISP IP address and stop encrypted traffic routing.",
                            color = Color(0xFF94A3B8),
                            fontSize = bodyFontSize,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Equal-Size Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldNeon),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = EmeraldNeon.copy(alpha = 0.12f),
                            contentColor = EmeraldNeon
                        )
                    ) {
                        Text(
                            text = "Stay Protected",
                            fontWeight = FontWeight.Bold,
                            fontSize = bodyFontSize,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Button(
                        onClick = onConfirmDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(buttonHeight),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonCoral,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Disconnect",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = bodyFontSize,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
