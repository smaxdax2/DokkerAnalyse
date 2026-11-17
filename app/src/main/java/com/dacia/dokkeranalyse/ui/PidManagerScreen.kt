package com.dacia.dokkeranalyse.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dacia.dokkeranalyse.domain.FormulaEvaluator
import com.dacia.dokkeranalyse.model.ObdPid
import com.dacia.dokkeranalyse.model.PidCategory
import com.dacia.dokkeranalyse.ui.theme.DokkerAmber
import com.dacia.dokkeranalyse.ui.theme.DokkerBlue
import com.dacia.dokkeranalyse.ui.theme.DokkerGreen
import com.dacia.dokkeranalyse.ui.theme.DokkerRed
import java.util.Locale

@Composable
fun PidManagerScreen(
    pids: List<ObdPid>,
    onToggleEnabled: (String) -> Unit,
    onSetPrimaryEvaporator: (String) -> Unit,
    onSavePid: (ObdPid) -> Unit,
    onDeletePid: (String) -> Unit,
    onResetDefaults: () -> Unit
) {
    var editingPid by remember { mutableStateOf<ObdPid?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingPid = ObdPid(
                        id = "",
                        name = "",
                        shortName = "",
                        description = "Custom Dacia Dokker OBD2 / UDS parameter",
                        category = PidCategory.CUSTOM,
                        txHeader = "7E0",
                        rxHeader = "7E8",
                        initCommand = "",
                        modeAndPid = "22",
                        formula = "A-40",
                        unit = "°C",
                        minValue = -40f,
                        maxValue = 120f,
                        decimals = 1,
                        enabled = true,
                        isPrimaryEvaporator = false
                    )
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Custom Value") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sensors & UDS PIDs",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enable/disable sensors, switch evaporator ECU profile, or add new custom values.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { showResetConfirm = true }) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }

            items(pids, key = { it.id }) { pid ->
                PidConfigCard(
                    pid = pid,
                    onToggle = { onToggleEnabled(pid.id) },
                    onSetPrimaryEvap = { onSetPrimaryEvaporator(pid.id) },
                    onEdit = { editingPid = pid },
                    onDelete = { onDeletePid(pid.id) }
                )
            }
        }
    }

    if (editingPid != null) {
        EditPidDialog(
            initialPid = editingPid!!,
            onDismiss = { editingPid = null },
            onSave = { updated ->
                onSavePid(updated)
                editingPid = null
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Restore Default Dokker PIDs?") },
            text = {
                Text("This will restore the default Dacia Dokker 1.5 dCi (2014) climate and engine sensor list.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDefaults()
                        showResetConfirm = false
                    }
                ) {
                    Text("Restore Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PidConfigCard(
    pid: ObdPid,
    onToggle: () -> Unit,
    onSetPrimaryEvap: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pid.isPrimaryEvaporator) {
                            Icon(
                                imageVector = Icons.Default.AcUnit,
                                contentDescription = "Primary Evaporator",
                                tint = DokkerBlue,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Text(
                            text = pid.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${pid.category.label} • Unit: ${pid.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = DokkerBlue
                    )
                }

                Switch(
                    checked = pid.enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            if (pid.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = pid.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tx:${pid.txHeader} Rx:${pid.rxHeader} | PID:${pid.modeAndPid} | ${pid.formula}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    IconButton(onClick = onSetPrimaryEvap) {
                        Icon(
                            imageVector = if (pid.isPrimaryEvaporator) {
                                Icons.Default.Star
                            } else {
                                Icons.Outlined.StarBorder
                            },
                            contentDescription = "Set as Primary Evaporator Card",
                            tint = if (pid.isPrimaryEvaporator) DokkerAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit PID"
                        )
                    }
                    if (!pid.isPrimaryEvaporator) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete PID",
                                tint = DokkerRed
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPidDialog(
    initialPid: ObdPid,
    onDismiss: () -> Unit,
    onSave: (ObdPid) -> Unit
) {
    var name by remember { mutableStateOf(initialPid.name) }
    var shortName by remember { mutableStateOf(initialPid.shortName) }
    var description by remember { mutableStateOf(initialPid.description) }
    var txHeader by remember { mutableStateOf(initialPid.txHeader) }
    var rxHeader by remember { mutableStateOf(initialPid.rxHeader) }
    var initCommand by remember { mutableStateOf(initialPid.initCommand) }
    var modeAndPid by remember { mutableStateOf(initialPid.modeAndPid) }
    var formula by remember { mutableStateOf(initialPid.formula) }
    var unit by remember { mutableStateOf(initialPid.unit) }
    var decimalsStr by remember { mutableStateOf(initialPid.decimals.toString()) }
    var isPrimaryEvap by remember { mutableStateOf(initialPid.isPrimaryEvaporator) }

    // Interactive formula test input (e.g. byte A=45, B=0)
    var testByteAHex by remember { mutableStateOf("2D") }
    var testByteBHex by remember { mutableStateOf("00") }

    val testA = testByteAHex.trim().toIntOrNull(16) ?: 0
    val testB = testByteBHex.trim().toIntOrNull(16) ?: 0
    val previewResult = FormulaEvaluator.evaluate(formula, listOf(testA, testB))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialPid.id.isBlank()) "Add Custom Sensor / PID" else "Edit Sensor / PID")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Sensor Name (e.g. Exhaust Temp)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shortName,
                        onValueChange = { shortName = it },
                        label = { Text("Short Label") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit (°C, bar, V)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = txHeader,
                        onValueChange = { txHeader = it.uppercase(Locale.ROOT) },
                        label = { Text("CAN Tx (744/7E0)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rxHeader,
                        onValueChange = { rxHeader = it.uppercase(Locale.ROOT) },
                        label = { Text("CAN Rx (764/7E8)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = modeAndPid,
                        onValueChange = { modeAndPid = it.uppercase(Locale.ROOT) },
                        label = { Text("Mode+PID (220003)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = initCommand,
                        onValueChange = { initCommand = it.uppercase(Locale.ROOT) },
                        label = { Text("Init Cmd (10C0)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = formula,
                        onValueChange = { formula = it },
                        label = { Text("Formula (A, B, C, D)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = decimalsStr,
                        onValueChange = { decimalsStr = it },
                        label = { Text("Decimals") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notes / Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Formula Preview Box
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Formula Tester (Hex Bytes -> Result)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = testByteAHex,
                                onValueChange = { testByteAHex = it.take(2).uppercase(Locale.ROOT) },
                                label = { Text("Byte A (Hex)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = testByteBHex,
                                onValueChange = { testByteBHex = it.take(2).uppercase(Locale.ROOT) },
                                label = { Text("Byte B (Hex)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (previewResult != null) {
                                String.format(
                                    Locale.ROOT,
                                    "A=%d (0x%02X), B=%d (0x%02X)  ⇒  %.2f %s",
                                    testA, testA, testB, testB, previewResult, unit
                                )
                            } else {
                                "Invalid formula syntax"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (previewResult != null) DokkerGreen else DokkerRed,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Show as Primary Evaporator Hero Card",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isPrimaryEvap,
                        onCheckedChange = { isPrimaryEvap = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim().ifEmpty { "Custom Sensor" }
                    val cleanShort = shortName.trim().ifEmpty { cleanName.take(10) }
                    onSave(
                        initialPid.copy(
                            name = cleanName,
                            shortName = cleanShort,
                            description = description.trim(),
                            txHeader = txHeader.trim().ifEmpty { "7E0" },
                            rxHeader = rxHeader.trim().ifEmpty { "7E8" },
                            initCommand = initCommand.trim(),
                            modeAndPid = modeAndPid.trim().replace(" ", "").ifEmpty { "0105" },
                            formula = formula.trim().ifEmpty { "A" },
                            unit = unit.trim(),
                            decimals = decimalsStr.toIntOrNull()?.coerceIn(0, 3) ?: 1,
                            isPrimaryEvaporator = isPrimaryEvap
                        )
                    )
                }
            ) {
                Text("Save Sensor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
