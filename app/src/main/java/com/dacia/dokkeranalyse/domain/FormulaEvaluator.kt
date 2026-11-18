package com.dacia.dokkeranalyse.domain

import java.util.Locale

/**
 * Evaluates OBD-II / UDS byte formulas such as:
 * - "A-40"
 * - "((A*256)+B)*0.1"
 * - "((A*256)+B)/4"
 * - "SIGNED(A)*0.5"
 * - "{A:0}" (bit 0 of byte A)
 */
object FormulaEvaluator {

    /**
     * Parses raw ELM327 voltage response from ATRV (e.g. "14.2V" or "12,4V").
     */
    fun parseAtRvVoltage(rawResponse: String): Double? {
        val clean = rawResponse.replace(",", ".")
        val match = Regex("([0-9]+\\.?[0-9]*)\\s*V", RegexOption.IGNORE_CASE).find(clean)
            ?: Regex("\\b([0-9]{1,2}\\.[0-9]{1,2})\\b").find(clean)
        return match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    /**
     * Parses raw ELM327 hex response for a given requested modeAndPid (e.g. "220003" or "0105")
     * and extracts the payload bytes [A, B, C, D, ...].
     */
    fun extractPayloadBytes(rawResponse: String, modeAndPid: String): List<Int>? {
        val cleanPid = modeAndPid.replace(" ", "").uppercase(Locale.ROOT)
        if (cleanPid == "ATRV") {
            val v = parseAtRvVoltage(rawResponse) ?: return null
            val mv = (v * 1000.0).toInt().coerceIn(0, 65535)
            return listOf((mv shr 8) and 0xFF, mv and 0xFF)
        }
        if (cleanPid.length < 2) return null

        val reqMode = cleanPid.substring(0, 2).toIntOrNull(16) ?: return null
        val positiveResponseMode = String.format(Locale.ROOT, "%02X", (reqMode + 0x40) and 0xFF)
        val pidRest = cleanPid.substring(2)
        val expectedPrefix = positiveResponseMode + pidRest

        // Clean lines from ELM327 output
        val lines = rawResponse
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("AT") &&
                    !line.startsWith("SEARCHING") &&
                    !line.startsWith("STOPPED") &&
                    !line.startsWith("NO DATA") &&
                    !line.startsWith("UNABLE") &&
                    !line.startsWith("ERROR") &&
                    !line.startsWith("?") &&
                    line != "OK" &&
                    line != ">"
            }

        if (lines.isEmpty()) return null

        // Combine hex tokens across lines (strips ISO-TP frame indices like "0:", "1:" if present)
        val hexBuilder = StringBuilder()
        for (line in lines) {
            val withoutFrameIndex = line.replace(Regex("^[0-9A-F]:"), "").trim()
            val cleanedHex = withoutFrameIndex.replace(" ", "")
            // Check for negative response 7F
            if (cleanedHex.startsWith("7F")) {
                return null
            }
            if (cleanedHex.all { it in '0'..'9' || it in 'A'..'F' }) {
                hexBuilder.append(cleanedHex)
            }
        }

        val fullHex = hexBuilder.toString()
        val matchIdx = fullHex.indexOf(expectedPrefix)
        if (matchIdx == -1) return null

        val payloadHex = fullHex.substring(matchIdx + expectedPrefix.length)
        if (payloadHex.length < 2) return null

        val bytes = mutableListOf<Int>()
        var i = 0
        while (i + 1 < payloadHex.length && bytes.size < 8) {
            val byteVal = payloadHex.substring(i, i + 2).toIntOrNull(16) ?: break
            bytes.add(byteVal)
            i += 2
        }
        return if (bytes.isNotEmpty()) bytes else null
    }

    /**
     * Evaluates a mathematical formula using payload bytes A, B, C, D.
     */
    fun evaluate(formula: String, bytes: List<Int>): Double? {
        if (bytes.isEmpty() || formula.isBlank()) return null
        return try {
            val a = bytes.getOrElse(0) { 0 }
            val b = bytes.getOrElse(1) { 0 }
            val c = bytes.getOrElse(2) { 0 }
            val d = bytes.getOrElse(3) { 0 }

            var expr = formula.uppercase(Locale.ROOT).replace(" ", "")

            // Replace {A:bit} bit extraction syntax
            expr = Regex("\\{([ABCD]):([0-7])\\}").replace(expr) { matchResult ->
                val byteName = matchResult.groupValues[1]
                val bitIndex = matchResult.groupValues[2].toInt()
                val byteVal = when (byteName) {
                    "A" -> a
                    "B" -> b
                    "C" -> c
                    "D" -> d
                    else -> 0
                }
                val bitVal = (byteVal shr bitIndex) and 1
                bitVal.toString()
            }

            // Replace SIGNED(X)
            expr = Regex("SIGNED\\(([ABCD])\\)").replace(expr) { matchResult ->
                val byteVal = when (matchResult.groupValues[1]) {
                    "A" -> a
                    "B" -> b
                    "C" -> c
                    "D" -> d
                    else -> 0
                }
                val signedVal = if (byteVal >= 128) byteVal - 256 else byteVal
                "($signedVal)"
            }

            // Replace standalone variables A, B, C, D
            val sb = StringBuilder()
            for (ch in expr) {
                when (ch) {
                    'A' -> sb.append("($a)")
                    'B' -> sb.append("($b)")
                    'C' -> sb.append("($c)")
                    'D' -> sb.append("($d)")
                    else -> sb.append(ch)
                }
            }

            val result = ExpressionParser(sb.toString()).parse()
            if (result.isNaN() || result.isInfinite()) null else result
        } catch (e: Exception) {
            null
        }
    }

    private class ExpressionParser(private val input: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            pos++
            ch = if (pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < input.length) {
                throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
            }
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        x = if (divisor == 0.0) 0.0 else x / divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            val x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code)
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected token at $pos")
            }
            return x
        }
    }
}
