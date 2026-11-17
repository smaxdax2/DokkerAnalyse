package com.dacia.dokkeranalyse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacia.dokkeranalyse.model.ProbeCandidateResult
import com.dacia.dokkeranalyse.model.TerminalLogEntry
import com.dacia.dokkeranalyse.ui.theme.DokkerBlue
import com.dacia.dokkeranalyse.ui.theme.DokkerGreen
import com.dacia.dokkeranalyse.ui.theme.DokkerRed
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    probeResults: List<ProbeCandidateResult>,
    isProbing: Boolean,
    terminalLogs: List<TerminalLogEntry>,
    onRunEvaporatorProbe: () -> Unit,
    onUseProbeCandidate: (ProbeCandidateResult) -> Unit,
    onSendRawCommand: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    var rawCmdInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Auto-Scanner for Dacia Dokker Evaporator Sensor ECUs
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Evaporator ECU Auto-Scanner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Probes Climate (744), Engine K9K (7E0/7E2), and UCH (745) to see which ECU hosts the evaporator thermistor on your 2014 Dokker.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onRunEvaporatorProbe,
                        enabled = !isProbing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isProbing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).width(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Probing Dokker ECUs...")
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan All Candidate Evaporator PIDs")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    probeResults.forEach { cand ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cand.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Tx:${cand.txHeader} Rx:${cand.rxHeader} | PID:${cand.modeAndPid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (cand.isSuccess && cand.decodedTempC != null) {
                                    FilledTonalButton(
                                        onClick = { onUseProbeCandidate(cand) }
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = DokkerGreen
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            String.format(
                                                Locale.ROOT,
                                                "Use (%.1f°C)",
                                                cand.decodedTempC
                                            )
                                        )
                                    }
                                }
                            }

                            if (cand.rawResponse.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Raw: ${cand.rawResponse.replace("\n", " ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (cand.isSuccess) DokkerGreen else DokkerRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Manual ELM327 / UDS Command Console
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ELM327 / UDS Raw Terminal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Send custom AT or UDS hex requests (e.g. ATRV, ATSH744, 10C0, 220003)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onClearLogs) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = rawCmdInput,
                            onValueChange = { rawCmdInput = it.uppercase(Locale.ROOT) },
                            label = { Text("AT / Hex Command") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (rawCmdInput.isNotBlank()) {
                                    onSendRawCommand(rawCmdInput)
                                    rawCmdInput = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }

        // 3. Log Output Entries
        items(terminalLogs.reversed(), key = { it.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "[${entry.timestamp}] > ${entry.command}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = DokkerBlue
                    )
                    Text(
                        text = entry.response,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (entry.isError) DokkerRed else DokkerGreen
                    )
                }
            }
        }
    }
}
