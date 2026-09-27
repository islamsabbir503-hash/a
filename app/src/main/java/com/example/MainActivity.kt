package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.data.model.TunnelConfig
import com.example.tunnel.WireGuardTunnelManager
import com.example.ui.MainScreen
import com.example.ui.theme.NitroWarpTheme
import com.example.ui.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {

    private val vpnViewModel: VpnViewModel by viewModels()
    private var pendingTunnelConfig: TunnelConfig? = null

    // Mandatory Android OS VPN System Permission Dialog Launcher ("Connection request - OK/Cancel")
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val config = pendingTunnelConfig
        pendingTunnelConfig = null
        if (result.resultCode == Activity.RESULT_OK && config != null) {
            Log.i("MainActivity", "Android VPN system permission GRANTED. Launching tunnel...")
            launchVpnServiceIntent(config)
        } else {
            Log.w("MainActivity", "Android VPN system permission DENIED by user.")
            vpnViewModel.disconnect { stopVpnTunnel() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WireGuardTunnelManager.init(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        setContent {
            NitroWarpTheme {
                MainScreen(
                    viewModel = vpnViewModel,
                    onStartTunnel = { config ->
                        startVpnTunnel(config)
                    },
                    onStopTunnel = {
                        stopVpnTunnel()
                    }
                )
            }
        }
    }

    private fun startVpnTunnel(config: TunnelConfig) {
        try {
            // CRITICAL: Check Android system VPN permission via VpnService.prepare(this)
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                pendingTunnelConfig = config
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                launchVpnServiceIntent(config)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error preparing VPN tunnel: ${e.message}", e)
            vpnViewModel.disconnect { stopVpnTunnel() }
        }
    }

    private fun launchVpnServiceIntent(config: TunnelConfig) {
        try {
            val intent = Intent(this, NitroWarpVpnService::class.java).apply {
                action = NitroWarpVpnService.ACTION_CONNECT
                putExtra(NitroWarpVpnService.EXTRA_SESSION, config.sessionName)
                putExtra(NitroWarpVpnService.EXTRA_ENGINE, config.engine)
                putExtra(NitroWarpVpnService.EXTRA_CLIENT_IP, config.clientIp)
                putExtra(NitroWarpVpnService.EXTRA_PREFIX_LENGTH, config.prefixLength)
                putExtra(NitroWarpVpnService.EXTRA_MTU, config.mtu)
                putExtra(NitroWarpVpnService.EXTRA_PRIVATE_KEY_B64, config.privateKeyB64)
                putExtra(NitroWarpVpnService.EXTRA_SERVER_PUB_KEY_B64, config.serverPublicKeyB64)
                putExtra(NitroWarpVpnService.EXTRA_ENDPOINT, config.endpoint)
                putExtra(NitroWarpVpnService.EXTRA_SERVER_IP, config.serverIp)
                putExtra(NitroWarpVpnService.EXTRA_SERVER_PORT, config.serverPort)
                putExtra(NitroWarpVpnService.EXTRA_PROTOCOL, config.protocol)
                putExtra(NitroWarpVpnService.EXTRA_CIPHER, config.cipher)
                putExtra(NitroWarpVpnService.EXTRA_COUNTRY_LONG, config.countryLong)
                putExtra(NitroWarpVpnService.EXTRA_COUNTRY_SHORT, config.countryShort)
                putExtra(NitroWarpVpnService.EXTRA_OVPN_BASE64, config.ovpnConfigBase64)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting VPN service: ${e.message}", e)
            vpnViewModel.disconnect { stopVpnTunnel() }
        }
    }

    private fun stopVpnTunnel() {
        try {
            val intent = Intent(this, NitroWarpVpnService::class.java).apply {
                action = NitroWarpVpnService.ACTION_DISCONNECT
            }
            startService(intent)
        } catch (e: Exception) {
            try {
                stopService(Intent(this, NitroWarpVpnService::class.java))
            } catch (_: Exception) {}
        }
    }
}
