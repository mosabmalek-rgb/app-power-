package com.example

import com.example.data.ReadingCycleCalculator
import com.example.data.ReadingCycleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.*

class ReadingCycleTest {

    @Test
    fun testEvery10DaysCycle() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        // Day 5 -> 1 to 10
        val date1 = sdf.parse("2026-10-05")!!
        val range1 = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.EVERY_10_DAYS, referenceDate = date1)
        assertTrue(range1.labelAr.contains("1 - 10"))

        // Day 15 -> 11 to 20
        val date2 = sdf.parse("2026-10-15")!!
        val range2 = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.EVERY_10_DAYS, referenceDate = date2)
        assertTrue(range2.labelAr.contains("11 - 20"))

        // Day 25 -> 21 to 31
        val date3 = sdf.parse("2026-10-25")!!
        val range3 = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.EVERY_10_DAYS, referenceDate = date3)
        assertTrue(range3.labelAr.contains("21 - 31"))
    }

    @Test
    fun testEvery15DaysCycle() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        
        val date1 = sdf.parse("2026-10-05")!!
        val range1 = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.EVERY_15_DAYS, referenceDate = date1)
        assertTrue(range1.labelAr.contains("1 - 15"))

        val date2 = sdf.parse("2026-10-20")!!
        val range2 = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.EVERY_15_DAYS, referenceDate = date2)
        assertTrue(range2.labelAr.contains("16 - 31"))
    }

    @Test
    fun testMonthlyCycle() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse("2026-10-05")!!
        val range = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.MONTHLY, referenceDate = date)
        assertTrue(range.labelAr.contains("1 - 31"))
    }

    @Test
    fun testMonthlyCycleDay30ClampsFebruaryTo28() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val february = sdf.parse("2026-02-20")!!
        val range = ReadingCycleCalculator.calculateCurrentCycleRange(
            ReadingCycleType.MONTHLY,
            monthlyStartDay = 30,
            referenceDate = february
        )
        assertTrue(range.labelAr.contains("تبدأ يوم 30"))
        assertTrue(range.shortRangeText.contains("30 يناير"))
    }

    @Test
    fun testWeeklyCycle() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val saturday = sdf.parse("2026-10-03")!! // Saturday
        val range = ReadingCycleCalculator.calculateCurrentCycleRange(ReadingCycleType.WEEKLY, weeklyStartDay = Calendar.SATURDAY, referenceDate = saturday)
        assertTrue(range.labelAr.contains("السبت"))
    }
}
