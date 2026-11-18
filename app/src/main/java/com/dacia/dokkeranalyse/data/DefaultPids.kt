package com.dacia.dokkeranalyse.data

import com.dacia.dokkeranalyse.model.ObdPid
import com.dacia.dokkeranalyse.model.PidCategory
import com.dacia.dokkeranalyse.model.ProbeCandidateResult

object DefaultPids {

    /**
     * Tailored specifically for HSN 8212 / TSN ABO (ABO000766) based on verified ECU log:
     * Dacia Dokker 1.5 dCi 75 PS (55 kW, K9K 612 Euro 5)
     * - Engine ECU: 7E0 / 7E8 using session 10C0 (50C0 verified)
     * - Verified A/C PIDs:
     *   - 22222A: AC Refrigerant Pressure (PR037) -> ((A*256)+B)*0.1 bar (5.5-5.7 bar resting)
     *   - 222229: AC Request & Evaporator Sensor Switch (ET018) -> {A:0} (1 = ON, 0 = OFF / Evap Cutoff)
     *   - 222092: AC Compressor Permission (ET116) -> {A:0} (1 = Allowed, 0 = Inhibited by Engine ECU)
     *   - 222091: AC / Fan Status Byte -> A
     */
    fun getDaciaDokker2014Defaults(): List<ObdPid> = listOf(
        ObdPid(
            id = "ac_pressure_k9k",
            name = "A/C System & Evaporator Monitor",
            shortName = "AC Press",
            description = "Live A/C High-Pressure sensor (22222A) combined with Evaporator Cutoff (AC Req 222229) & Engine ECU Permission (AC Perm 222092).",
            category = PidCategory.CLIMATE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "22222A",
            formula = "((A*256)+B)*0.1",
            unit = "bar",
            minValue = 0f,
            maxValue = 30f,
            decimals = 1,
            enabled = true,
            isPrimaryEvaporator = true
        ),
        ObdPid(
            id = "ac_request_k9k",
            name = "AC Request / Evap Switch (ET018)",
            shortName = "AC Req (Evap)",
            description = "1 = A/C switch ON & Evaporator Sensor OK. Drops to 0 if Evaporator Sensor (277230127R) trips or A/C is turned off (DID 222229).",
            category = PidCategory.CLIMATE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "222229",
            formula = "{A:0}",
            unit = "state",
            minValue = 0f,
            maxValue = 1f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "ac_permission_k9k",
            name = "AC Compressor Permission (ET116)",
            shortName = "AC Perm (ECU)",
            description = "1 = Engine ECU permits compressor clutch engagement, 0 = Cut off / Inhibited by Engine ECU (DID 222092).",
            category = PidCategory.CLIMATE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "222092",
            formula = "{A:0}",
            unit = "state",
            minValue = 0f,
            maxValue = 1f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "coolant_temp",
            name = "Engine Coolant Temperature",
            shortName = "Coolant",
            description = "Standard OBD2 Engine Coolant Temperature (K9K 612 Mode 0105).",
            category = PidCategory.ENGINE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "",
            modeAndPid = "0105",
            formula = "A-40",
            unit = "°C",
            minValue = -40f,
            maxValue = 130f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "intake_temp",
            name = "Intake Air Temperature",
            shortName = "Intake Air",
            description = "Manifold intake air temperature (K9K 612 Mode 010F).",
            category = PidCategory.CLIMATE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "",
            modeAndPid = "010F",
            formula = "A-40",
            unit = "°C",
            minValue = -40f,
            maxValue = 100f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "engine_rpm",
            name = "Engine Speed (RPM)",
            shortName = "Engine RPM",
            description = "Shows idle speed rise (typically ~850–900 rpm) when the A/C compressor clutch engages (Mode 010C).",
            category = PidCategory.ENGINE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "",
            modeAndPid = "010C",
            formula = "((A*256)+B)/4",
            unit = "rpm",
            minValue = 0f,
            maxValue = 5000f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "battery_voltage",
            name = "System Voltage (OBD2 ATRV)",
            shortName = "Voltage",
            description = "12V battery and alternator charging voltage read directly via ELM327 ATRV.",
            category = PidCategory.ELECTRICAL,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "",
            modeAndPid = "ATRV",
            formula = "((A*256)+B)/1000",
            unit = "V",
            minValue = 9f,
            maxValue = 16f,
            decimals = 2,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "k9k_rail_pressure_obd",
            name = "Fuel Rail Pressure (Mode 01)",
            shortName = "Rail Press",
            description = "Common-rail diesel injection pressure via standard Mode 0123 on K9K 612 (~250–300 bar at idle).",
            category = PidCategory.ENGINE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "",
            modeAndPid = "0123",
            formula = "((A*256)+B)*0.1",
            unit = "bar",
            minValue = 0f,
            maxValue = 1800f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "ac_status_2091",
            name = "A/C & Cooling Status Byte (222091)",
            shortName = "AC Stat 2091",
            description = "Raw A/C & fan status byte on K9K 612 (verified responding 62 20 91 0F on 8212/ABO).",
            category = PidCategory.CLIMATE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "222091",
            formula = "A",
            unit = "raw",
            minValue = 0f,
            maxValue = 255f,
            decimals = 0,
            enabled = true,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "k9k_boost_map",
            name = "Intake Manifold Pressure (MAP)",
            shortName = "MAP / Boost",
            description = "Absolute intake manifold pressure via Mode 010B (~1.0 bar at idle, up to ~2.2 bar under boost).",
            category = PidCategory.ENGINE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "",
            modeAndPid = "010B",
            formula = "A*0.01",
            unit = "bar",
            minValue = 0f,
            maxValue = 2.5f,
            decimals = 2,
            enabled = false,
            isPrimaryEvaporator = false
        ),
        ObdPid(
            id = "k9k_dpf_soot",
            name = "DPF Soot Mass (22242C)",
            shortName = "DPF Soot",
            description = "Particulate filter soot load in grams (DID 22242C).",
            category = PidCategory.ENGINE,
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "22242C",
            formula = "((A*256)+B)/100",
            unit = "g",
            minValue = 0f,
            maxValue = 40f,
            decimals = 2,
            enabled = false,
            isPrimaryEvaporator = false
        )
    )

    /**
     * Verified A/C & Climate DIDs on Dacia Dokker 1.5 dCi 75 PS (8212 / ABO000766).
     */
    fun getEvaporatorProbeCandidates(): List<ProbeCandidateResult> = listOf(
        ProbeCandidateResult(
            label = "K9K (7E0/7E8) - AC Refrigerant Pressure (22222A / PR037)",
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "22222A",
            formula = "((A*256)+B)*0.1",
            rawResponse = "",
            decodedTempC = null,
            isSuccess = false
        ),
        ProbeCandidateResult(
            label = "K9K (7E0/7E8) - AC Request / Evap Switch (222229 / ET018)",
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "222229",
            formula = "{A:0}",
            rawResponse = "",
            decodedTempC = null,
            isSuccess = false
        ),
        ProbeCandidateResult(
            label = "K9K (7E0/7E8) - AC Compressor Permission (222092 / ET116)",
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "222092",
            formula = "{A:0}",
            rawResponse = "",
            decodedTempC = null,
            isSuccess = false
        ),
        ProbeCandidateResult(
            label = "K9K (7E0/7E8) - AC Status Mask (222091)",
            txHeader = "7E0",
            rxHeader = "7E8",
            initCommand = "10C0",
            modeAndPid = "222091",
            formula = "A",
            rawResponse = "",
            decodedTempC = null,
            isSuccess = false
        ),
        ProbeCandidateResult(
            label = "UCH T4_VS_BCM (745/765) - Block 228015 (UDS 1003)",
            txHeader = "745",
            rxHeader = "765",
            initCommand = "1003",
            modeAndPid = "228015",
            formula = "A",
            rawResponse = "",
            decodedTempC = null,
            isSuccess = false
        ),
        ProbeCandidateResult(
            label = "UCH T4_VS_BCM (745/765) - Block 228018 (UDS 1003)",
            txHeader = "745",
            rxHeader = "765",
            initCommand = "1003",
            modeAndPid = "228018",
            formula = "A",
            rawResponse = "",
            decodedTempC = null,
            isSuccess = false
        )
    )

    /**
     * Deep UDS DID sweep list for K9K (7E0/7E8) and UCH (745/765).
     */
    fun getDeepScanList(): List<Triple<String, String, String>> = listOf(
        Triple("7E0", "7E8", "222091"),
        Triple("7E0", "7E8", "222092"),
        Triple("7E0", "7E8", "222228"),
        Triple("7E0", "7E8", "222229"),
        Triple("7E0", "7E8", "22222A"),
        Triple("7E0", "7E8", "22222B"),
        Triple("7E0", "7E8", "22222C"),
        Triple("7E0", "7E8", "22222D"),
        Triple("7E0", "7E8", "22222E"),
        Triple("745", "765", "228010"),
        Triple("745", "765", "228015"),
        Triple("745", "765", "228016"),
        Triple("745", "765", "228017"),
        Triple("745", "765", "228018")
    )
}
