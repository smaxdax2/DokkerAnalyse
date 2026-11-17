package com.dacia.dokkeranalyse.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dacia.dokkeranalyse.bluetooth.Elm327BluetoothManager
import com.dacia.dokkeranalyse.data.DefaultPids
import com.dacia.dokkeranalyse.data.PidRepository
import com.dacia.dokkeranalyse.domain.FormulaEvaluator
import com.dacia.dokkeranalyse.model.BluetoothDeviceItem
import com.dacia.dokkeranalyse.model.ConnectionState
import com.dacia.dokkeranalyse.model.ObdPid
import com.dacia.dokkeranalyse.model.PidReading
import com.dacia.dokkeranalyse.model.ProbeCandidateResult
import com.dacia.dokkeranalyse.model.TerminalLogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PidRepository(application.applicationContext)
    private val btManager = Elm327BluetoothManager(application.applicationContext)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Disconnected — Select Bluetooth OBD2 adapter")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceItem>> = _pairedDevices.asStateFlow()

    private val _pids = MutableStateFlow<List<ObdPid>>(emptyList())
    val pids: StateFlow<List<ObdPid>> = _pids.asStateFlow()

    private val _readings = MutableStateFlow<Map<String, PidReading>>(emptyMap())
    val readings: StateFlow<Map<String, PidReading>> = _readings.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalLogEntry>> = _terminalLogs.asStateFlow()

    private val _probeResults = MutableStateFlow<List<ProbeCandidateResult>>(
        DefaultPids.getEvaporatorProbeCandidates()
    )
    val probeResults: StateFlow<List<ProbeCandidateResult>> = _probeResults.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private var pollingJob: Job? = null
    private var logCounter = 0L
    private var demoTick = 0.0

    init {
        _pids.value = repository.loadPids()
        refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        _pairedDevices.value = btManager.getPairedDevices()
    }

    fun getLastSavedDeviceAddress(): String? = repository.getLastDeviceAddress()

    fun connectToDevice(device: BluetoothDeviceItem) {
        pollingJob?.cancel()
        viewModelScope.launch {
            _connectionState.value = ConnectionState.CONNECTING
            _connectedDeviceName.value = device.name
            _statusMessage.value = "Connecting to ${device.name} (${device.address})..."

            val result = btManager.connectAndInitialize(
                address = device.address,
                onLog = { cmd, resp, isErr -> appendLog(cmd, resp, isErr) }
            )

            result.fold(
                onSuccess = { adapterVer ->
                    repository.saveLastDeviceAddress(device.address)
                    _connectionState.value = ConnectionState.CONNECTED
                    _statusMessage.value = "Connected ($adapterVer) — Polling live sensors"
                    startPollingLoop()
                },
                onFailure = { err ->
                    _connectionState.value = ConnectionState.ERROR
                    _statusMessage.value = err.message ?: "Connection failed"
                    appendLog("CONNECT", err.message ?: "Connection failed", true)
                }
            )
        }
    }

    fun startDemoMode() {
        pollingJob?.cancel()
        viewModelScope.launch {
            btManager.disconnect()
            _connectionState.value = ConnectionState.DEMO_MODE
            _connectedDeviceName.value = "Simulated Dokker 1.5 dCi"
            _statusMessage.value = "Demo Simulation Active — Simulating A/C evaporator cooling & icing cycle"
            appendLog("DEMO", "Started Dacia Dokker 1.5 dCi A/C simulation", false)
            startDemoLoop()
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        viewModelScope.launch {
            btManager.disconnect()
            _connectionState.value = ConnectionState.DISCONNECTED
            _connectedDeviceName.value = null
            _statusMessage.value = "Disconnected"
        }
    }

    private fun startPollingLoop() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                if (_isProbing.value) {
                    delay(300L)
                    continue
                }

                // Group enabled PIDs by ECU header so we minimize ATSH / ATCRA switching per cycle
                val enabledPids = _pids.value
                    .filter { it.enabled }
                    .sortedWith(compareBy({ it.txHeader }, { it.rxHeader }))

                if (enabledPids.isEmpty()) {
                    delay(500L)
                    continue
                }

                for (pid in enabledPids) {
                    if (!isActive || _connectionState.value != ConnectionState.CONNECTED || _isProbing.value) {
                        break
                    }
                    try {
                        val rawResp = btManager.queryPid(
                            txHeader = pid.txHeader,
                            rxHeader = pid.rxHeader,
                            initCommand = pid.initCommand,
                            modeAndPid = pid.modeAndPid
                        )

                        val bytes = FormulaEvaluator.extractPayloadBytes(rawResp, pid.modeAndPid)
                        val evaluated = if (bytes != null) {
                            FormulaEvaluator.evaluate(pid.formula, bytes)
                        } else {
                            null
                        }

                        updatePidReading(
                            pidId = pid.id,
                            value = evaluated,
                            rawHex = rawResp,
                            bytes = bytes ?: emptyList(),
                            error = if (evaluated == null) rawResp.take(32) else null
                        )
                    } catch (e: Exception) {
                        updatePidReading(
                            pidId = pid.id,
                            value = null,
                            rawHex = "",
                            bytes = emptyList(),
                            error = e.message ?: "Read error"
                        )
                        // If socket broke, exit loop
                        if (e is java.io.IOException) {
                            _connectionState.value = ConnectionState.ERROR
                            _statusMessage.value = "Connection lost: ${e.message}"
                            break
                        }
                    }
                    delay(60L)
                }
            }
        }
    }

    private fun startDemoLoop() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _connectionState.value == ConnectionState.DEMO_MODE) {
                demoTick += 0.18
                val enabledPids = _pids.value.filter { it.enabled }
                for (pid in enabledPids) {
                    // Simulate realistic Dokker 1.5 dCi values
                    val simulatedVal: Double = when {
                        pid.isPrimaryEvaporator || pid.id.startsWith("evap_") -> {
                            // Oscillate between +8.5°C (normal cooling) down to +0.4°C (icing threshold)
                            4.5 + 4.2 * sin(demoTick * 0.4)
                        }
                        pid.id == "ac_pressure_k9k" -> {
                            12.4 + 2.8 * sin(demoTick * 0.4)
                        }
                        pid.id == "coolant_temp" -> {
                            84.0 + 1.5 * sin(demoTick * 0.1)
                        }
                        pid.id == "ambient_temp" -> {
                            26.0
                        }
                        pid.id == "engine_rpm" -> {
                            850.0 + 15.0 * sin(demoTick)
                        }
                        pid.id == "battery_voltage" -> {
                            14.18 + 0.06 * sin(demoTick * 0.7)
                        }
                        pid.id == "k9k_rail_pressure" -> {
                            285.0 + 12.0 * sin(demoTick * 0.8)
                        }
                        else -> {
                            ((pid.minValue + pid.maxValue) / 2.0) + 5.0 * sin(demoTick * 0.5)
                        }
                    }

                    val simulatedByteA = (simulatedVal + 40).toInt().coerceIn(0, 255)
                    val hexByte = String.format(Locale.ROOT, "%02X", simulatedByteA)
                    val modePrefix = if (pid.modeAndPid.startsWith("22")) {
                        "62" + pid.modeAndPid.drop(2)
                    } else {
                        "41" + pid.modeAndPid.drop(2)
                    }

                    updatePidReading(
                        pidId = pid.id,
                        value = simulatedVal,
                        rawHex = "$modePrefix$hexByte",
                        bytes = listOf(simulatedByteA),
                        error = null
                    )
                }
                delay(500L)
            }
        }
    }

    private fun updatePidReading(
        pidId: String,
        value: Double?,
        rawHex: String,
        bytes: List<Int>,
        error: String?
    ) {
        _readings.update { currentMap ->
            val prev = currentMap[pidId]
            val newMin = when {
                value == null -> prev?.minRecorded
                prev?.minRecorded == null -> value
                else -> minOf(prev.minRecorded, value)
            }
            val newMax = when {
                value == null -> prev?.maxRecorded
                prev?.maxRecorded == null -> value
                else -> maxOf(prev.maxRecorded, value)
            }
            val prevHistory = prev?.history ?: emptyList()
            val newHistory = if (value != null) {
                (prevHistory + value).takeLast(60)
            } else {
                prevHistory
            }

            currentMap + (
                pidId to PidReading(
                    pidId = pidId,
                    value = value ?: prev?.value,
                    rawHex = rawHex,
                    dataBytes = bytes,
                    timestampMs = System.currentTimeMillis(),
                    minRecorded = newMin,
                    maxRecorded = newMax,
                    history = newHistory,
                    error = error
                )
            )
        }
    }

    fun resetMinMaxHistory() {
        _readings.value = emptyMap()
    }

    // --- PID Management ---

    fun togglePidEnabled(pidId: String) {
        val updated = _pids.value.map {
            if (it.id == pidId) it.copy(enabled = !it.enabled) else it
        }
        _pids.value = updated
        repository.savePids(updated)
    }

    fun setPrimaryEvaporatorPid(pidId: String) {
        val updated = _pids.value.map {
            if (it.id == pidId) {
                it.copy(isPrimaryEvaporator = true, enabled = true)
            } else {
                it.copy(isPrimaryEvaporator = false)
            }
        }
        _pids.value = updated
        repository.savePids(updated)
    }

    fun saveOrUpdatePid(pid: ObdPid) {
        val existingIndex = _pids.value.indexOfFirst { it.id == pid.id }
        val baseList = if (pid.isPrimaryEvaporator) {
            _pids.value.map { it.copy(isPrimaryEvaporator = false) }
        } else {
            _pids.value
        }

        val updated = if (existingIndex >= 0) {
            baseList.toMutableList().apply { set(existingIndex, pid) }
        } else {
            val newId = if (pid.id.isBlank()) "custom_${UUID.randomUUID().toString().take(8)}" else pid.id
            baseList + pid.copy(id = newId)
        }
        _pids.value = updated
        repository.savePids(updated)
    }

    fun deletePid(pidId: String) {
        val updated = _pids.value.filterNot { it.id == pidId }
        _pids.value = updated
        repository.savePids(updated)
    }

    fun resetPidsToDefault() {
        _pids.value = repository.resetToDefaults()
        _readings.value = emptyMap()
    }

    // --- Diagnostics & Evaporator PID Auto-Scanner ---

    fun probeEvaporatorCandidates() {
        if (_isProbing.value) return
        viewModelScope.launch {
            _isProbing.value = true
            val candidates = DefaultPids.getEvaporatorProbeCandidates()
            val updatedResults = mutableListOf<ProbeCandidateResult>()

            for (cand in candidates) {
                if (_connectionState.value == ConnectionState.DEMO_MODE) {
                    delay(250L)
                    val isPrimarySim = cand.txHeader == "744" && cand.modeAndPid == "220003"
                    val simRaw = if (isPrimarySim) "6200032D" else "NO DATA"
                    val simTemp = if (isPrimarySim) 5.0 else null
                    updatedResults.add(
                        cand.copy(
                            rawResponse = simRaw,
                            decodedTempC = simTemp,
                            isSuccess = isPrimarySim
                        )
                    )
                    _probeResults.value = updatedResults + candidates.drop(updatedResults.size)
                    continue
                }

                if (_connectionState.value != ConnectionState.CONNECTED) {
                    updatedResults.add(
                        cand.copy(
                            rawResponse = "NOT CONNECTED",
                            decodedTempC = null,
                            isSuccess = false
                        )
                    )
                    continue
                }

                val raw = try {
                    btManager.queryPid(
                        txHeader = cand.txHeader,
                        rxHeader = cand.rxHeader,
                        initCommand = cand.initCommand,
                        modeAndPid = cand.modeAndPid,
                        onLog = { c, r, err -> appendLog(c, r, err) }
                    )
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }

                val bytes = FormulaEvaluator.extractPayloadBytes(raw, cand.modeAndPid)
                val temp = if (bytes != null) FormulaEvaluator.evaluate(cand.formula, bytes) else null
                val plausible = temp != null && temp in -35.0..85.0

                updatedResults.add(
                    cand.copy(
                        rawResponse = raw,
                        decodedTempC = temp,
                        isSuccess = plausible
                    )
                )
                _probeResults.value = updatedResults + candidates.drop(updatedResults.size)
            }
            _isProbing.value = false
        }
    }

    fun applyProbeCandidateAsPrimary(candidate: ProbeCandidateResult) {
        val currentPrimary = _pids.value.firstOrNull { it.isPrimaryEvaporator }
            ?: _pids.value.firstOrNull()

        if (currentPrimary != null) {
            saveOrUpdatePid(
                currentPrimary.copy(
                    name = "AC Evaporator Temp (${candidate.txHeader})",
                    txHeader = candidate.txHeader,
                    rxHeader = candidate.rxHeader,
                    initCommand = candidate.initCommand,
                    modeAndPid = candidate.modeAndPid,
                    formula = candidate.formula,
                    enabled = true,
                    isPrimaryEvaporator = true
                )
            )
        }
    }

    fun sendManualCommand(cmd: String) {
        if (cmd.isBlank()) return
        viewModelScope.launch {
            if (_connectionState.value == ConnectionState.DEMO_MODE) {
                appendLog(cmd.uppercase(Locale.ROOT), "OK (DEMO MODE)", false)
                return@launch
            }
            if (_connectionState.value != ConnectionState.CONNECTED) {
                appendLog(cmd.uppercase(Locale.ROOT), "NOT CONNECTED", true)
                return@launch
            }
            try {
                btManager.sendRawCommand(cmd) { c, r, isErr ->
                    appendLog(c, r, isErr)
                }
            } catch (e: Exception) {
                appendLog(cmd, "ERROR: ${e.message}", true)
            }
        }
    }

    fun clearLogs() {
        _terminalLogs.value = emptyList()
    }

    private fun appendLog(cmd: String, resp: String, isError: Boolean) {
        val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
        val entry = TerminalLogEntry(
            id = ++logCounter,
            timestamp = timeFmt,
            command = cmd,
            response = resp,
            isError = isError
        )
        _terminalLogs.update { (it + entry).takeLast(120) }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        viewModelScope.launch {
            btManager.disconnect()
        }
    }
}
