package com.dacia.dokkeranalyse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dacia.dokkeranalyse.data.DefaultPids
import com.dacia.dokkeranalyse.model.BluetoothDeviceItem
import com.dacia.dokkeranalyse.model.ConnectionState
import com.dacia.dokkeranalyse.model.ObdPid
import com.dacia.dokkeranalyse.model.PidReading
import com.dacia.dokkeranalyse.ui.theme.DokkerAmber
import com.dacia.dokkeranalyse.ui.theme.DokkerAnalyseTheme
import com.dacia.dokkeranalyse.ui.theme.DokkerBlue
import com.dacia.dokkeranalyse.ui.theme.DokkerGreen
import com.dacia.dokkeranalyse.ui.theme.DokkerRed
import java.util.Locale

@Composable
fun DashboardScreen(
    connectionState: ConnectionState,
    statusMessage: String,
    connectedDeviceName: String?,
    pairedDevices: List<BluetoothDeviceItem>,
    lastDeviceAddress: String?,
    pids: List<ObdPid>,
    readings: Map<String, PidReading>,
    onRequestPermissionsAndRefreshDevices: () -> Unit,
    onConnectDevice: (BluetoothDeviceItem) -> Unit,
    onStartDemo: () -> Unit,
    onDisconnect: () -> Unit,
    onResetStats: () -> Unit,
    onNavigateToPidManager: () -> Unit
) {
    var showBluetoothDialog by remember { mutableStateOf(false) }

    val primaryEvapPid = pids.firstOrNull { it.isPrimaryEvaporator }
        ?: pids.firstOrNull { it.id.startsWith("evap_") }
    val otherEnabledPids = pids.filter { it.enabled && it.id != primaryEvapPid?.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Connection Card
        item {
            ConnectionStatusCard(
                connectionState = connectionState,
                statusMessage = statusMessage,
                connectedDeviceName = connectedDeviceName,
                onSelectAdapterClick = {
                    onRequestPermissionsAndRefreshDevices()
                    showBluetoothDialog = true
                },
                onStartDemoClick = onStartDemo,
                onDisconnectClick = onDisconnect
            )
        }

        // 2. Hero Card: AC Evaporator Temperature Sensor
        if (primaryEvapPid != null) {
            item {
                EvaporatorHeroCard(
                    pid = primaryEvapPid,
                    reading = readings[primaryEvapPid.id],
                    onResetStats = onResetStats
                )
            }
        }

        // 3. Section header for additional live parameters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Additional Live Values (${otherEnabledPids.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                AssistChip(
                    onClick = onNavigateToPidManager,
                    label = { Text("Add / Configure") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }

        // 4. Secondary Sensor Cards (2 per row)
        val chunkedPids = otherEnabledPids.chunked(2)
        items(chunkedPids) { rowPids ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (pid in rowPids) {
                    SecondaryPidCard(
                        pid = pid,
                        reading = readings[pid.id],
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowPids.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (showBluetoothDialog) {
        BluetoothDevicePickerDialog(
            devices = pairedDevices,
            lastAddress = lastDeviceAddress,
            onRefresh = onRequestPermissionsAndRefreshDevices,
            onSelect = { device ->
                showBluetoothDialog = false
                onConnectDevice(device)
            },
            onDismiss = { showBluetoothDialog = false }
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    statusMessage: String,
    connectedDeviceName: String?,
    onSelectAdapterClick: () -> Unit,
    onStartDemoClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val dotColor = when (connectionState) {
        ConnectionState.CONNECTED -> DokkerGreen
        ConnectionState.DEMO_MODE -> DokkerBlue
        ConnectionState.CONNECTING, ConnectionState.INITIALIZING -> DokkerAmber
        ConnectionState.ERROR -> DokkerRed
        ConnectionState.DISCONNECTED -> Color.Gray
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = connectedDeviceName ?: "Dacia Dokker 1.5 dCi (2014)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (connectionState == ConnectionState.CONNECTED ||
                    connectionState == ConnectionState.DEMO_MODE ||
                    connectionState == ConnectionState.CONNECTING
                ) {
                    Button(
                        onClick = onDisconnectClick,
                        colors = ButtonDefaults.buttonColors(containerColor = DokkerRed),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onSelectAdapterClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Connect OBD2")
                    }
                    FilledTonalButton(
                        onClick = onStartDemoClick
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Demo")
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaporatorHeroCard(
    pid: ObdPid,
    reading: PidReading?,
    onResetStats: () -> Unit
) {
    val tempVal = reading?.value
    val formattedValue = if (tempVal != null) {
        String.format(Locale.ROOT, "%.${pid.decimals}f", tempVal)
    } else {
        "--.-"
    }

    // Climate diagnostic evaluation for Dacia Dokker evaporator sensor
    val (statusLabel, statusColor, diagnosticHint) = when {
        tempVal == null -> Triple(
            "WAITING FOR ECU DATA",
            Color.Gray,
            "Connect to ELM327 adapter and turn ignition ON (ECU ${pid.txHeader} -> PID ${pid.modeAndPid})."
        )
        tempVal < -25.0 || tempVal > 75.0 -> Triple(
            "SENSOR FAULT / OPEN CIRCUIT",
            DokkerRed,
            "Extreme value (${formattedValue}°C) points to a defective NTC thermistor or broken wire."
        )
        tempVal <= 1.5 -> Triple(
            "ICING / FREEZING RISK!",
            DokkerRed,
            "Evaporator core is near or below 0°C! Risk of ice blocking airflow or cutting off compressor."
        )
        tempVal in 1.5..10.5 -> Triple(
            "OPTIMAL A/C COOLING",
            DokkerGreen,
            "Normal evaporator temperature range (2°C – 10°C) while A/C compressor is active."
        )
        else -> Triple(
            "WARM / A/C COMPRESSOR OFF",
            DokkerAmber,
            "Evaporator is above 11°C. A/C is off, clutch disengaged, or refrigerant pressure is low."
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AcUnit,
                        contentDescription = null,
                        tint = DokkerBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = pid.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Header: ${pid.txHeader}/${pid.rxHeader} • PID: ${pid.modeAndPid} • Formula: ${pid.formula}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onResetStats) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Min/Max"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big Temperature Readout + Min/Max Column
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formattedValue,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (tempVal != null) statusColor else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pid.unit,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val minStr = reading?.minRecorded?.let {
                        String.format(Locale.ROOT, "%.1f %s", it, pid.unit)
                    } ?: "--"
                    val maxStr = reading?.maxRecorded?.let {
                        String.format(Locale.ROOT, "%.1f %s", it, pid.unit)
                    } ?: "--"
                    Text(
                        text = "MIN: $minStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = DokkerBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "MAX: $maxStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = DokkerAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!reading?.rawHex.isNullOrBlank()) {
                        Text(
                            text = "HEX: ${reading?.rawHex?.replace("\n", " ")?.take(18)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostic Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = diagnosticHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Sparkline chart (last 60 readings)
            val history = reading?.history ?: emptyList()
            if (history.size >= 2) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Live Evaporator Temperature Trend (0°C Icing Threshold Marked)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                EvaporatorSparkline(
                    history = history,
                    lineColor = statusColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun EvaporatorSparkline(
    history: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (history.size < 2) return@Canvas
        val minVal = minOf(history.minOrNull() ?: -5.0, -2.0)
        val maxVal = maxOf(history.maxOrNull() ?: 20.0, 15.0)
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        // Draw 2°C icing warning threshold dashed/reference line
        val icingY = size.height - (((2.0 - minVal) / range).toFloat() * size.height)
        if (icingY in 0f..size.height) {
            drawLine(
                color = DokkerRed.copy(alpha = 0.45f),
                start = Offset(0f, icingY),
                end = Offset(size.width, icingY),
                strokeWidth = 2f
            )
        }

        val path = Path()
        history.forEachIndexed { idx, value ->
            val x = (idx.toFloat() / (history.size - 1).coerceAtLeast(1)) * size.width
            val y = size.height - (((value - minVal) / range).toFloat() * size.height)
            if (idx == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4f)
        )
    }
}

@Composable
private fun SecondaryPidCard(
    pid: ObdPid,
    reading: PidReading?,
    modifier: Modifier = Modifier
) {
    val value = reading?.value
    val formatted = if (value != null) {
        String.format(Locale.ROOT, "%.${pid.decimals}f", value)
    } else {
        "--"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = pid.shortName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatted,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pid.unit,
                    style = MaterialTheme.typography.bodyMedium,
                    color = DokkerBlue,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (reading?.error != null && value == null) {
                    reading.error
                } else {
                    "${pid.txHeader} • ${pid.modeAndPid}"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (reading?.error != null && value == null) {
                    DokkerRed
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BluetoothDevicePickerDialog(
    devices: List<BluetoothDeviceItem>,
    lastAddress: String?,
    onRefresh: () -> Unit,
    onSelect: (BluetoothDeviceItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Select Bluetooth OBD2 Adapter")
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh paired devices")
                }
            }
        },
        text = {
            if (devices.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "No paired Bluetooth devices found.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "1. Plug your ELM327 / OBDLink adapter into the Dokker OBD2 port (inside the glovebox/fuse compartment).\n" +
                            "2. Pair it once in Android Settings -> Bluetooth (PIN is usually 1234 or 0000).\n" +
                            "3. Tap Refresh above.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(devices) { dev ->
                        val isLast = dev.address == lastAddress
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (dev.isLikelyObdAdapter || isLast) {
                                        DokkerBlue.copy(alpha = 0.14f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable { onSelect(dev) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (dev.isLikelyObdAdapter) {
                                    Icons.Default.BluetoothConnected
                                } else {
                                    Icons.Default.Bluetooth
                                },
                                contentDescription = null,
                                tint = if (dev.isLikelyObdAdapter) DokkerBlue else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.name,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = buildString {
                                        append(dev.address)
                                        if (dev.isLikelyObdAdapter) append(" • OBD Adapter")
                                        if (isLast) append(" • Last Used")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
fun DashboardScreenPreview() {
    val samplePids = DefaultPids.getDaciaDokker2014Defaults()
    val sampleReadings = mapOf(
        "evap_clim_744" to PidReading(
            pidId = "evap_clim_744",
            value = 4.2,
            rawHex = "6200032C",
            minRecorded = 1.8,
            maxRecorded = 14.5,
            history = listOf(12.0, 9.5, 7.2, 5.8, 4.9, 4.4, 4.1, 3.9, 4.2)
        ),
        "ac_pressure_k9k" to PidReading(pidId = "ac_pressure_k9k", value = 12.6, rawHex = "6240067E"),
        "coolant_temp" to PidReading(pidId = "coolant_temp", value = 85.0, rawHex = "41057D"),
        "ambient_temp" to PidReading(pidId = "ambient_temp", value = 27.0, rawHex = "414643"),
        "engine_rpm" to PidReading(pidId = "engine_rpm", value = 850.0, rawHex = "410C0D48"),
        "battery_voltage" to PidReading(pidId = "battery_voltage", value = 14.21, rawHex = "41423782")
    )
    DokkerAnalyseTheme {
        DashboardScreen(
            connectionState = ConnectionState.CONNECTED,
            statusMessage = "Connected (ELM327 v1.5) — Polling live sensors",
            connectedDeviceName = "OBDII ELM327",
            pairedDevices = emptyList(),
            lastDeviceAddress = null,
            pids = samplePids,
            readings = sampleReadings,
            onRequestPermissionsAndRefreshDevices = {},
            onConnectDevice = {},
            onStartDemo = {},
            onDisconnect = {},
            onResetStats = {},
            onNavigateToPidManager = {}
        )
    }
}
