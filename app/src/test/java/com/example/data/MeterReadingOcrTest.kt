package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeterReadingOcrTest {
    @Test
    fun normalizesArabicIndicDigitsAndDecimalSeparator() {
        assertEquals("128.52", MeterReadingOcr.findReadingCandidate("قراءة العداد: ١٢٨٫٥٢"))
    }

    @Test
    fun supportsEasternArabicDigitsAndCommaDecimal() {
        assertEquals("94.5", MeterReadingOcr.findReadingCandidate("Meter: ۹۴,۵"))
    }

    @Test
    fun rejectsMoreThanTwoDecimalPlaces() {
        assertNull(MeterReadingOcr.findReadingCandidate("Reading 128.523"))
    }

    @Test
    fun choosesTheProminentLongMeterValueOverShortLabels() {
        assertEquals("1258", MeterReadingOcr.findReadingCandidate("No 3\nReading\n1258"))
    }
}
