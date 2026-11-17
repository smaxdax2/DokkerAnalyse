package com.dacia.dokkeranalyse.model

enum class PidCategory(val label: String) {
    CLIMATE("Climate / A/C"),
    ENGINE("Engine (1.5 dCi K9K)"),
    ELECTRICAL("Electrical / UCH"),
    CUSTOM("Custom Sensor")
}

data class ObdPid(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String = "",
    val category: PidCategory = PidCategory.CUSTOM,
    val txHeader: String = "7E0",
    val rxHeader: String = "7E8",
    val initCommand: String = "",
    val modeAndPid: String = "220003",
    val formula: String = "A-40",
    val unit: String = "°C",
    val minValue: Float = -40f,
    val maxValue: Float = 100f,
    val decimals: Int = 1,
    val enabled: Boolean = true,
    val isPrimaryEvaporator: Boolean = false
)

data class PidReading(
    val pidId: String,
    val value: Double? = null,
    val rawHex: String = "",
    val dataBytes: List<Int> = emptyList(),
    val timestampMs: Long = 0L,
    val minRecorded: Double? = null,
    val maxRecorded: Double? = null,
    val history: List<Double> = emptyList(),
    val error: String? = null
)

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    INITIALIZING,
    CONNECTED,
    DEMO_MODE,
    ERROR
}

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isLikelyObdAdapter: Boolean
)

data class TerminalLogEntry(
    val id: Long,
    val timestamp: String,
    val command: String,
    val response: String,
    val isError: Boolean = false
)

data class ProbeCandidateResult(
    val label: String,
    val txHeader: String,
    val rxHeader: String,
    val initCommand: String,
    val modeAndPid: String,
    val formula: String,
    val rawResponse: String,
    val decodedTempC: Double?,
    val isSuccess: Boolean
)
