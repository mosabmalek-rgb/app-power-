package com.example.data

import java.text.SimpleDateFormat
import java.util.*

enum class ReadingCycleType(val code: String, val titleAr: String, val descriptionAr: String) {
    WEEKLY("WEEKLY", "كل أسبوع", "أسبوعياً مع تحديد يوم بدء الدورة"),
    EVERY_10_DAYS("EVERY_10_DAYS", "كل 10 أيام (1، 11، 21)", "تقسيم الشهر إلى 3 دورات: (1-10)، (11-20)، (21-نهاية الشهر)"),
    EVERY_15_DAYS("EVERY_15_DAYS", "كل 15 يوم (1، 16)", "تقسيم الشهر إلى دورتين: (1-15)، (16-نهاية الشهر)"),
    MONTHLY("MONTHLY", "كل شهر (1 أو 30 الشهر \"عدا شهر فبراير 28\")", "دورة شهرية منتظمة تغطي دورة القراءات من بداية الشهر حتى نهايته")
}

enum class SubscriberCycleFilter(val titleAr: String) {
    ALL("الكل"),
    UNREAD_THIS_CYCLE("لم تُسجل لهذه الدورة"),
    READ_THIS_CYCLE("تمت قراءتها")
}

data class CycleDateRange(
    val cycleType: ReadingCycleType,
    val startMillis: Long,
    val endMillis: Long,
    val labelAr: String,
    val shortRangeText: String,
    val timeZoneId: String = "Asia/Gaza",
    val cycleKey: String? = null
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

    fun getDayNameAr(dayOfWeek: Int): String {
        return ARABIC_DAYS.find { it.first == dayOfWeek }?.second ?: "السبت"
    }

    private fun safeTimeZone(value: String): TimeZone {
        val requested = value.trim().ifBlank { "Asia/Gaza" }
        val resolved = TimeZone.getTimeZone(requested)
        return if (resolved.id == "GMT" && requested != "GMT" && requested != "UTC") {
            TimeZone.getTimeZone("Asia/Gaza")
        } else resolved
    }

    private fun parseServerInstant(value: String): Long? {
        val normalized = Regex("""\.(\d+)(?=(Z|[+-]\d{2}:\d{2})$)""").replace(value.trim()) { match ->
            "." + match.groupValues[1].padEnd(3, '0').take(3)
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(normalized)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

    fun fromServerBounds(
        type: ReadingCycleType,
        cycleKey: String,
        startAt: String,
        endAt: String,
        label: String,
        timeZoneId: String
    ): CycleDateRange? {
        val start = parseServerInstant(startAt) ?: return null
        val end = parseServerInstant(endAt) ?: return null
        if (cycleKey.isBlank() || start >= end) return null
        val display = label.ifBlank { cycleKey }
        return CycleDateRange(
            cycleType = type,
            startMillis = start,
            endMillis = end,
            labelAr = display,
            shortRangeText = display,
            timeZoneId = safeTimeZone(timeZoneId).id,
            cycleKey = cycleKey
        )
    }

    fun calculateCurrentCycleRange(
        type: ReadingCycleType,
        weeklyStartDay: Int = Calendar.SATURDAY,
        referenceDate: Date = Date()
    ): CycleDateRange {
        val cal = Calendar.getInstance()
        cal.time = referenceDate

        val displayFormat = SimpleDateFormat("d MMMM", Locale("ar"))
        val fullDisplayFormat = SimpleDateFormat("d MMMM yyyy", Locale("ar"))
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("ar"))

        return when (type) {
            ReadingCycleType.WEEKLY -> {
                val currentDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                var diff = currentDayOfWeek - weeklyStartDay
                if (diff < 0) {
                    diff += 7
                }

                val startCal = Calendar.getInstance().apply {
                    time = referenceDate
                    add(Calendar.DAY_OF_YEAR, -diff)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endCal = Calendar.getInstance().apply {
                    time = startCal.time
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val dayName = getDayNameAr(weeklyStartDay)
                val label = "دورة أسبوعية ($dayName: ${displayFormat.format(startCal.time)} - ${fullDisplayFormat.format(endCal.time)})"
                val shortText = "${displayFormat.format(startCal.time)} - ${displayFormat.format(endCal.time)}"

                CycleDateRange(
                    cycleType = type,
                    startMillis = startCal.timeInMillis,
                    endMillis = endCal.timeInMillis,
                    labelAr = label,
                    shortRangeText = shortText
                )
            }

            ReadingCycleType.EVERY_10_DAYS -> {
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val (startDay, endDay, labelSuffix) = when {
                    dayOfMonth in 1..10 -> Triple(1, 10, "دورة 1 - 10")
                    dayOfMonth in 11..20 -> Triple(11, 20, "دورة 11 - 20")
                    else -> Triple(21, maxDay, "دورة 21 - $maxDay")
                }

                val startCal = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, startDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endCal = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, endDay)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val monthName = SimpleDateFormat("MMMM", Locale("ar")).format(referenceDate)
                val label = "$labelSuffix $monthName ${cal.get(Calendar.YEAR)}"
                val shortText = "$startDay - $endDay $monthName"

                CycleDateRange(
                    cycleType = type,
                    startMillis = startCal.timeInMillis,
                    endMillis = endCal.timeInMillis,
                    labelAr = label,
                    shortRangeText = shortText
                )
            }

            ReadingCycleType.EVERY_15_DAYS -> {
                val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val (startDay, endDay, labelSuffix) = if (dayOfMonth in 1..15) {
                    Triple(1, 15, "دورة 1 - 15")
                } else {
                    Triple(16, maxDay, "دورة 16 - $maxDay")
                }

                val startCal = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, startDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endCal = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, endDay)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val monthName = SimpleDateFormat("MMMM", Locale("ar")).format(referenceDate)
                val label = "$labelSuffix $monthName ${cal.get(Calendar.YEAR)}"
                val shortText = "$startDay - $endDay $monthName"

                CycleDateRange(
                    cycleType = type,
                    startMillis = startCal.timeInMillis,
                    endMillis = endCal.timeInMillis,
                    labelAr = label,
                    shortRangeText = shortText
                )
            }

            ReadingCycleType.MONTHLY -> {
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

                val startCal = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val endCal = Calendar.getInstance().apply {
                    time = referenceDate
                    set(Calendar.DAY_OF_MONTH, maxDay)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                val label = "دورة شهر ${monthYearFormat.format(referenceDate)} (1 - $maxDay)"
                val shortText = "شهر ${monthYearFormat.format(referenceDate)}"

                CycleDateRange(
                    cycleType = type,
                    startMillis = startCal.timeInMillis,
                    endMillis = endCal.timeInMillis,
                    labelAr = label,
                    shortRangeText = shortText
                )
            }
        }
    }

    fun parseDateToTimestamp(dateStr: String?, timeZoneId: String = "Asia/Gaza"): Long? {
        if (dateStr.isNullOrBlank()) return null
        val value = dateStr.trim()
        if (value.contains('T')) return parseServerInstant(value)
        val zone = safeTimeZone(timeZoneId)
        val patterns = if (value.length >= 19) {
            listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        } else if (value.length >= 16) {
            listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd")
        } else {
            listOf("yyyy-MM-dd", "yyyy-MM-dd HH:mm")
        }
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = zone
                }.parse(value)
            }.getOrNull()
            if (parsed != null) return parsed.time
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
        if (subscriber.hasReadingInCurrentCycle &&
            (cycleRange.cycleKey == null || subscriber.currentCycleKey == cycleRange.cycleKey)
        ) return true

        if (pendingReadings.any { reading ->
                reading.subscriberId == subscriber.id && (
                    (cycleRange.cycleKey != null && reading.readingCycleKey == cycleRange.cycleKey) ||
                        (reading.readingCycleKey == null && isDateInCycle(reading.readingDate, cycleRange))
                    )
            }) return true

        return isDateInCycle(subscriber.lastReadingDate, cycleRange)
    }
}
