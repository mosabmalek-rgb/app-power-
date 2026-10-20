package com.example.data

import java.text.SimpleDateFormat
import java.util.*

enum class ReadingCycleType(val code: String, val titleAr: String, val descriptionAr: String) {
    WEEKLY("WEEKLY", "كل أسبوع", "أسبوعياً مع تحديد يوم بدء الدورة"),
    EVERY_10_DAYS("EVERY_10_DAYS", "كل 10 أيام", "دورات متكررة حسب إعداد المنصة"),
    EVERY_15_DAYS("EVERY_15_DAYS", "كل 15 يوماً", "دورات متكررة حسب إعداد المنصة"),
    MONTHLY("MONTHLY", "كل شهر", "دورة شهرية حسب إعداد المنصة")
}

enum class SubscriberCycleFilter(val titleAr: String) {
    ALL("الكل"),
    UNREAD_THIS_CYCLE("لم تُسجل لهذه الدورة"),
    READ_THIS_CYCLE("تمت قراءتها")
}

/** Read-only snapshot of the cycle policy returned by PowerHub. */
data class ReadingCycleConfiguration(
    val cycleType: ReadingCycleType = ReadingCycleType.MONTHLY,
    val weeklyStartDay: Int = Calendar.SATURDAY,
    val monthlyStartDay: Int = 1,
    val timeZoneId: String = "Asia/Gaza"
)

data class CycleDateRange(
    val cycleType: ReadingCycleType,
    val startMillis: Long,
    val endMillis: Long,
    val labelAr: String,
    val shortRangeText: String,
    val timeZoneId: String = "Asia/Gaza"
)

object ReadingCycleCalculator {
    val ARABIC_DAYS = listOf(
        Pair(Calendar.SATURDAY, "السبت"),
        Pair(Calendar.SUNDAY, "الأحد"),
        Pair(Calendar.MONDAY, "الإثنين"),
        Pair(Calendar.TUESDAY, "الثلاثاء"),
        Pair(Calendar.WEDNESDAY, "الأربعاء"),
        Pair(Calendar.THURSDAY, "الخميس"),
        Pair(Calendar.FRIDAY, "الجمعة")
    )

    fun getDayNameAr(dayOfWeek: Int): String =
        ARABIC_DAYS.find { it.first == dayOfWeek }?.second ?: "السبت"

    private fun safeTimeZone(timeZoneId: String): TimeZone {
        val zone = TimeZone.getTimeZone(timeZoneId)
        return if (timeZoneId.isBlank() || (zone.id == "GMT" && timeZoneId != "GMT" && timeZoneId != "UTC")) {
            TimeZone.getTimeZone("Asia/Gaza")
        } else zone
    }

    private fun calendarAtMidnight(date: Date, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone, Locale.US).apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun monthStart(calendar: Calendar): Calendar =
        (calendar.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun shiftMonth(calendar: Calendar, months: Int): Calendar =
        monthStart(calendar).apply { add(Calendar.MONTH, months) }

    private fun dayInMonth(calendar: Calendar, requestedDay: Int): Calendar =
        monthStart(calendar).apply {
            set(Calendar.DAY_OF_MONTH, requestedDay.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }

    fun calculateCurrentCycleRange(
        type: ReadingCycleType,
        weeklyStartDay: Int = Calendar.SATURDAY,
        monthlyStartDay: Int = 1,
        timeZoneId: String = "Asia/Gaza",
        referenceDate: Date = Date()
    ): CycleDateRange {
        val timeZone = safeTimeZone(timeZoneId)
        val today = calendarAtMidnight(referenceDate, timeZone)
        val dayOfMonth = today.get(Calendar.DAY_OF_MONTH)
        val monthLastDay = today.getActualMaximum(Calendar.DAY_OF_MONTH)
        val start: Calendar
        val nextStart: Calendar

        when (type) {
            ReadingCycleType.WEEKLY -> {
                val normalizedStartDay = weeklyStartDay.coerceIn(Calendar.SUNDAY, Calendar.SATURDAY)
                val difference = (today.get(Calendar.DAY_OF_WEEK) - normalizedStartDay + 7) % 7
                start = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -difference) }
                nextStart = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
            }
            ReadingCycleType.EVERY_10_DAYS -> {
                if (monthlyStartDay == 30) {
                    val anchorDay = minOf(30, monthLastDay)
                    when {
                        dayOfMonth >= anchorDay -> {
                            start = dayInMonth(today, anchorDay)
                            nextStart = dayInMonth(shiftMonth(today, 1), 10)
                        }
                        dayOfMonth >= 20 -> {
                            start = dayInMonth(today, 20)
                            nextStart = dayInMonth(today, anchorDay)
                        }
                        dayOfMonth >= 10 -> {
                            start = dayInMonth(today, 10)
                            nextStart = dayInMonth(today, 20)
                        }
                        else -> {
                            start = dayInMonth(shiftMonth(today, -1), 30)
                            nextStart = dayInMonth(today, 10)
                        }
                    }
                } else {
                    val startDay = when {
                        dayOfMonth <= 10 -> 1
                        dayOfMonth <= 20 -> 11
                        else -> 21
                    }
                    start = dayInMonth(today, startDay)
                    nextStart = when (startDay) {
                        1 -> dayInMonth(today, 11)
                        11 -> dayInMonth(today, 21)
                        else -> dayInMonth(shiftMonth(today, 1), 1)
                    }
                }
            }
            ReadingCycleType.EVERY_15_DAYS -> {
                if (monthlyStartDay == 30) {
                    val anchorDay = minOf(30, monthLastDay)
                    when {
                        dayOfMonth >= anchorDay -> {
                            start = dayInMonth(today, anchorDay)
                            nextStart = dayInMonth(shiftMonth(today, 1), 15)
                        }
                        dayOfMonth >= 15 -> {
                            start = dayInMonth(today, 15)
                            nextStart = dayInMonth(today, anchorDay)
                        }
                        else -> {
                            start = dayInMonth(shiftMonth(today, -1), 30)
                            nextStart = dayInMonth(today, 15)
                        }
                    }
                } else {
                    val startDay = if (dayOfMonth <= 15) 1 else 16
                    start = dayInMonth(today, startDay)
                    nextStart = if (startDay == 1) dayInMonth(today, 16) else dayInMonth(shiftMonth(today, 1), 1)
                }
            }
            ReadingCycleType.MONTHLY -> {
                if (monthlyStartDay == 30) {
                    val anchorDay = minOf(30, monthLastDay)
                    start = if (dayOfMonth >= anchorDay) {
                        dayInMonth(today, anchorDay)
                    } else {
                        dayInMonth(shiftMonth(today, -1), 30)
                    }
                    nextStart = dayInMonth(shiftMonth(start, 1), 30)
                } else {
                    start = monthStart(today)
                    nextStart = dayInMonth(shiftMonth(today, 1), 1)
                }
            }
        }

        val endInclusive = Date(nextStart.timeInMillis - 1L)
        val locale = Locale("ar")
        val shortDate = SimpleDateFormat("d MMMM", locale).apply { timeZone = timeZone }
        val fullDate = SimpleDateFormat("d MMMM yyyy", locale).apply { timeZone = timeZone }
        val monthYear = SimpleDateFormat("MMMM yyyy", locale).apply { timeZone = timeZone }
        val endCalendar = calendarAtMidnight(endInclusive, timeZone)

        val label = when {
            type == ReadingCycleType.WEEKLY ->
                "دورة أسبوعية (${getDayNameAr(weeklyStartDay)}: ${shortDate.format(start.time)} - ${fullDate.format(endInclusive)})"
            type == ReadingCycleType.EVERY_10_DAYS && monthlyStartDay != 30 -> {
                val suffix = when (start.get(Calendar.DAY_OF_MONTH)) {
                    1 -> "دورة 1 - 10"
                    11 -> "دورة 11 - 20"
                    else -> "دورة 21 - ${endCalendar.get(Calendar.DAY_OF_MONTH)}"
                }
                "$suffix ${monthYear.format(start.time)}"
            }
            type == ReadingCycleType.EVERY_15_DAYS && monthlyStartDay != 30 -> {
                val suffix = if (start.get(Calendar.DAY_OF_MONTH) == 1) "دورة 1 - 15"
                    else "دورة 16 - ${endCalendar.get(Calendar.DAY_OF_MONTH)}"
                "$suffix ${monthYear.format(start.time)}"
            }
            type == ReadingCycleType.MONTHLY && monthlyStartDay != 30 ->
                "دورة شهر ${monthYear.format(start.time)} (1 - ${endCalendar.get(Calendar.DAY_OF_MONTH)})"
            else -> "${type.titleAr}: ${fullDate.format(start.time)} - ${fullDate.format(endInclusive)}"
        }

        return CycleDateRange(
            cycleType = type,
            startMillis = start.timeInMillis,
            endMillis = nextStart.timeInMillis - 1L,
            labelAr = label,
            shortRangeText = "${shortDate.format(start.time)} - ${shortDate.format(endInclusive)}",
            timeZoneId = timeZone.id
        )
    }

    fun parseDateToTimestamp(dateStr: String?, timeZoneId: String = "Asia/Gaza"): Long? {
        if (dateStr.isNullOrBlank()) return null
        val value = dateStr.trim()
        val timeZone = safeTimeZone(timeZoneId)
        val patterns = when {
            value.contains('T') -> listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            value.length >= 10 && value[4] == '-' && value[7] == '-' -> listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
            )
            else -> listOf("yyyy-MM-dd HH:mm")
        }
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    this.timeZone = if (pattern.endsWith("'Z'")) TimeZone.getTimeZone("UTC") else timeZone
                }
                parser.parse(value)?.let { return it.time }
            } catch (_: Exception) {
                // Try another supported representation.
            }
        }
        return null
    }

    fun isDateInCycle(dateStr: String?, cycleRange: CycleDateRange): Boolean {
        val timestamp = parseDateToTimestamp(dateStr, cycleRange.timeZoneId) ?: return false
        return timestamp in cycleRange.startMillis..cycleRange.endMillis
    }

    fun isSubscriberReadInCycle(
        subscriber: SubscriberEntity,
        pendingReadings: List<PendingReadingEntity>,
        cycleRange: CycleDateRange
    ): Boolean {
        if (pendingReadings.any { reading ->
                reading.subscriberId == subscriber.id && isDateInCycle(reading.readingDate, cycleRange)
            }) return true
        return isDateInCycle(subscriber.lastReadingDate, cycleRange)
    }
}
