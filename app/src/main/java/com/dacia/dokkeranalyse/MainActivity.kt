package com.dacia.dokkeranalyse

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dacia.dokkeranalyse.ui.DashboardScreen
import com.dacia.dokkeranalyse.ui.DiagnosticsScreen
import com.dacia.dokkeranalyse.ui.PidManagerScreen
import com.dacia.dokkeranalyse.ui.theme.DokkerAnalyseTheme
import com.dacia.dokkeranalyse.viewmodel.ObdViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DokkerAnalyseTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                val connectionState by viewModel.connectionState.collectAsState()
                val statusMessage by viewModel.statusMessage.collectAsState()
                val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
                val pairedDevices by viewModel.pairedDevices.collectAsState()
                val pids by viewModel.pids.collectAsState()
                val readings by viewModel.readings.collectAsState()
                val terminalLogs by viewModel.terminalLogs.collectAsState()
                val probeResults by viewModel.probeResults.collectAsState()
                val isProbing by viewModel.isProbing.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    viewModel.refreshPairedDevices()
                }

                val requestBtPermissionsAndRefresh = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    }
                    permissionLauncher.launch(perms)
                    viewModel.refreshPairedDevices()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Dokker 1.5 dCi Climate & OBD",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                    label = { Text("Dashboard") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    label = { Text("Sensors / PIDs") }
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                                    label = { Text("ECU Scanner") }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (selectedTab) {
                                0 -> DashboardScreen(
                                    connectionState = connectionState,
                                    statusMessage = statusMessage,
                                    connectedDeviceName = connectedDeviceName,
                                    pairedDevices = pairedDevices,
                                    lastDeviceAddress = viewModel.getLastSavedDeviceAddress(),
                                    pids = pids,
                                    readings = readings,
                                    onRequestPermissionsAndRefreshDevices = requestBtPermissionsAndRefresh,
                                    onConnectDevice = { viewModel.connectToDevice(it) },
                                    onStartDemo = { viewModel.startDemoMode() },
                                    onDisconnect = { viewModel.disconnect() },
                                    onResetStats = { viewModel.resetMinMaxHistory() },
                                    onNavigateToPidManager = { selectedTab = 1 }
                                )
                                1 -> PidManagerScreen(
                                    pids = pids,
                                    onToggleEnabled = { viewModel.togglePidEnabled(it) },
                                    onSetPrimaryEvaporator = { viewModel.setPrimaryEvaporatorPid(it) },
                                    onSavePid = { viewModel.saveOrUpdatePid(it) },
                                    onDeletePid = { viewModel.deletePid(it) },
                                    onResetDefaults = { viewModel.resetPidsToDefault() }
                                )
                                2 -> DiagnosticsScreen(
                                    probeResults = probeResults,
                                    isProbing = isProbing,
                                    terminalLogs = terminalLogs,
                                    onRunEvaporatorProbe = { viewModel.probeEvaporatorCandidates() },
                                    onUseProbeCandidate = { cand ->
                                        viewModel.applyProbeCandidateAsPrimary(cand)
                                        selectedTab = 0
                                    },
                                    onSendRawCommand = { viewModel.sendManualCommand(it) },
                                    onClearLogs = { viewModel.clearLogs() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
