package com.dacia.dokkeranalyse.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.dacia.dokkeranalyse.model.BluetoothDeviceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

class Elm327BluetoothManager(context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val commandMutex = Mutex()

    private var activeTxHeader: String = ""
    private var activeRxHeader: String = ""
    private var activeInitCommand: String = ""

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceItem> {
        val adapter = bluetoothAdapter ?: return emptyList()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return emptyList()

        return bonded.map { device ->
            val name = device.name ?: "Unknown Device"
            val upper = name.uppercase(Locale.ROOT)
            val isObd = OBD_KEYWORDS.any { upper.contains(it) }
            BluetoothDeviceItem(
                name = name,
                address = device.address,
                isLikelyObdAdapter = isObd
            )
        }.sortedWith(
            compareByDescending<BluetoothDeviceItem> { it.isLikelyObdAdapter }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndInitialize(
        address: String,
        onLog: (cmd: String, resp: String, isError: Boolean) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            closeInternal()
            val adapter = bluetoothAdapter
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth not supported"))

            if (!adapter.isEnabled) {
                return@withContext Result.failure(IllegalStateException("Bluetooth is turned off"))
            }

            val device: BluetoothDevice = try {
                adapter.getRemoteDevice(address)
            } catch (e: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Invalid Bluetooth MAC address: $address"))
            }

            runCatching { adapter.cancelDiscovery() }

            val connectedSocket = tryConnectSocket(device)
                ?: return@withContext Result.failure(
                    IOException("Could not open RFCOMM socket to ${device.name ?: address}. Make sure ignition is ON and adapter is plugged in.")
                )

            socket = connectedSocket
            inputStream = connectedSocket.inputStream
            outputStream = connectedSocket.outputStream

            activeTxHeader = ""
            activeRxHeader = ""
            activeInitCommand = ""

            // Initialize ELM327 for Dacia Dokker (2014) ISO 15765-4 CAN (11 bit ID, 500 kbaud)
            val initSequence = listOf(
                "ATZ" to 1500L,      // Reset ELM327
                "ATE0" to 500L,      // Echo Off
                "ATL0" to 500L,      // Linefeeds Off
                "ATS0" to 500L,      // Spaces Off
                "ATH0" to 500L,      // Headers Off
                "ATAT1" to 500L,     // Adaptive Timing Auto1
                "ATAL" to 500L,      // Allow Long (>7 byte) messages
                "ATSP6" to 800L      // ISO 15765-4 CAN (11 bit ID, 500 kbaud)
            )

            var adapterVersion = "ELM327"
            for ((cmd, timeoutMs) in initSequence) {
                val resp = sendCommandRawInternal(cmd, timeoutMs)
                onLog(cmd, resp, false)
                if (cmd == "ATZ" && resp.isNotBlank()) {
                    adapterVersion = resp.replace("\r", " ").replace("\n", " ").trim()
                }
            }

            Result.success(adapterVersion)
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnectSocket(device: BluetoothDevice): BluetoothSocket? {
        // Attempt 1: Standard secure SPP RFCOMM socket
        try {
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            return s
        } catch (_: Exception) {
        }

        // Attempt 2: Insecure SPP RFCOMM socket (often preferred by ELM327 v1.5 clones)
        try {
            val s = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            return s
        } catch (_: Exception) {
        }

        // Attempt 3: Reflection fallback to RFCOMM channel 1
        return try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val s = method.invoke(device, 1) as BluetoothSocket
            s.connect()
            s
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Switches ECU CAN Tx/Rx headers and Flow Control configuration if different from the currently active ECU.
     */
    suspend fun queryPid(
        txHeader: String,
        rxHeader: String,
        initCommand: String,
        modeAndPid: String,
        onLog: ((cmd: String, resp: String, isError: Boolean) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val cleanTx = txHeader.trim().uppercase(Locale.ROOT).ifEmpty { "7E0" }
            val cleanRx = rxHeader.trim().uppercase(Locale.ROOT).ifEmpty { "7E8" }
            val cleanInit = initCommand.trim().uppercase(Locale.ROOT)
            val cleanPid = modeAndPid.trim().replace(" ", "").uppercase(Locale.ROOT)

            // Native ELM327 adapter command (e.g. ATRV for battery voltage) does not need CAN header changes
            if (cleanPid.startsWith("AT")) {
                val atResp = sendCommandRawInternal(cleanPid, 600L)
                val isAtErr = atResp.contains("ERROR") || atResp.contains("?") || atResp.contains("TIMEOUT")
                onLog?.invoke(cleanPid, atResp, isAtErr)
                return@withContext atResp
            }

            if (cleanTx != activeTxHeader || cleanRx != activeRxHeader) {
                val headerCmds = listOf(
                    "ATSH$cleanTx",
                    "ATCRA$cleanRx",
                    "ATFCSH$cleanTx",
                    "ATFCSD300000",
                    "ATFCSM1"
                )
                for (hCmd in headerCmds) {
                    val hResp = sendCommandRawInternal(hCmd, 350L)
                    onLog?.invoke(hCmd, hResp, false)
                }
                activeTxHeader = cleanTx
                activeRxHeader = cleanRx
                activeInitCommand = ""
            }

            if (cleanInit.isNotEmpty() && cleanInit != activeInitCommand) {
                val initResp = sendCommandRawInternal(cleanInit, 650L)
                onLog?.invoke(cleanInit, initResp, initResp.startsWith("7F"))
                // If UDS 1003 is rejected with 7F, automatically try KWP 10C0 (and vice versa)
                if (initResp.replace(" ", "").startsWith("7F")) {
                    val altInit = if (cleanInit == "1003") "10C0" else if (cleanInit == "10C0") "1003" else ""
                    if (altInit.isNotEmpty()) {
                        val altResp = sendCommandRawInternal(altInit, 650L)
                        onLog?.invoke(altInit, altResp, altResp.startsWith("7F"))
                    }
                }
                activeInitCommand = cleanInit
            }

            val response = sendCommandRawInternal(cleanPid, 1000L)
            val isErr = response.contains("NO DATA") ||
                response.contains("ERROR") ||
                response.contains("UNABLE") ||
                response.startsWith("7F")
            onLog?.invoke(cleanPid, response, isErr)
            response
        }
    }

    suspend fun sendRawCommand(
        command: String,
        onLog: ((cmd: String, resp: String, isError: Boolean) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val clean = command.trim().uppercase(Locale.ROOT)
            if (clean.startsWith("ATSH") || clean.startsWith("ATCRA") || clean == "ATZ") {
                activeTxHeader = ""
                activeRxHeader = ""
                activeInitCommand = ""
            }
            val resp = sendCommandRawInternal(clean, 1500L)
            val isErr = resp.contains("ERROR") || resp.contains("?") || resp.contains("NO DATA")
            onLog?.invoke(clean, resp, isErr)
            resp
        }
    }

    private suspend fun sendCommandRawInternal(command: String, timeoutMs: Long): String {
        val out = outputStream ?: throw IOException("Not connected")
        val input = inputStream ?: throw IOException("Not connected")

        // Drain any stale bytes in input buffer
        try {
            while (input.available() > 0) {
                input.read()
            }
        } catch (_: Exception) {
        }

        val cmdBytes = "$command\r".toByteArray(Charsets.US_ASCII)
        out.write(cmdBytes)
        out.flush()

        val sb = StringBuilder()
        withTimeoutOrNull(timeoutMs) {
            val buffer = ByteArray(256)
            while (true) {
                if (input.available() > 0) {
                    val readCount = input.read(buffer)
                    if (readCount <= 0) break
                    val chunk = String(buffer, 0, readCount, Charsets.US_ASCII)
                    sb.append(chunk)
                    if (chunk.contains('>')) {
                        break
                    }
                } else {
                    kotlinx.coroutines.delay(15L)
                }
            }
        }

        return sb.toString()
            .replace(">", "")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(command, ignoreCase = true) }
            .joinToString("\n")
            .ifEmpty { "TIMEOUT / NO RESPONSE" }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            closeInternal()
        }
    }

    private fun closeInternal() {
        runCatching { inputStream?.close() }
        runCatching { outputStream?.close() }
        runCatching { socket?.close() }
        inputStream = null
        outputStream = null
        socket = null
        activeTxHeader = ""
        activeRxHeader = ""
        activeInitCommand = ""
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val OBD_KEYWORDS = listOf(
            "OBD", "ELM", "VGATE", "VLINKER", "KONNWEI", "CARISTA", "SCAN", "V-LINK", "IOS-VLINK", "CHX"
        )
    }
}
