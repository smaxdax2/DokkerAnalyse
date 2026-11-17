package com.dacia.dokkeranalyse.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FormulaEvaluatorTest {

    @Test
    fun `extracts payload bytes from UDS Mode 22 response`() {
        // Request 220003 -> Positive response 620003 followed by 0x2D (45 -> 45 - 40 = 5.0°C)
        val bytes = FormulaEvaluator.extractPayloadBytes("62 00 03 2D", "220003")
        assertNotNull(bytes)
        assertEquals(listOf(0x2D), bytes)

        val temp = FormulaEvaluator.evaluate("A-40", bytes!!)
        assertEquals(5.0, temp!!, 0.001)
    }

    @Test
    fun `extracts payload bytes from standard OBD Mode 01 response`() {
        // Request 010C (RPM) -> Positive response 410C 0D 48 -> ((13*256)+72)/4 = 850 rpm
        val bytes = FormulaEvaluator.extractPayloadBytes("410C0D48", "010C")
        assertNotNull(bytes)
        assertEquals(listOf(0x0D, 0x48), bytes)

        val rpm = FormulaEvaluator.evaluate("((A*256)+B)/4", bytes!!)
        assertEquals(850.0, rpm!!, 0.001)
    }

    @Test
    fun `evaluates SIGNED byte and bit extraction`() {
        val bytes = listOf(0xFE) // 254 unsigned -> -2 signed
        val res = FormulaEvaluator.evaluate("SIGNED(A)*0.5", bytes)
        assertEquals(-1.0, res!!, 0.001)

        val bit0 = FormulaEvaluator.evaluate("{A:1}", bytes)
        assertEquals(1.0, bit0!!, 0.001)
    }
}
