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
    val shortRangeText: String
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

    fun parseDateToTimestamp(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        val trimmed = dateStr.trim()
        return try {
            if (trimmed.length >= 10 && trimmed[4] == '-' && trimmed[7] == '-') {
                val ymd = trimmed.substring(0, 10)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.parse(ymd)?.time
            } else {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                sdf.parse(trimmed)?.time
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isDateInCycle(dateStr: String?, cycleRange: CycleDateRange): Boolean {
        val timestamp = parseDateToTimestamp(dateStr) ?: return false
        return timestamp in cycleRange.startMillis..cycleRange.endMillis
    }

    fun isSubscriberReadInCycle(
        subscriber: SubscriberEntity,
        pendingReadings: List<PendingReadingEntity>,
        cycleRange: CycleDateRange
    ): Boolean {
        val hasPendingInCycle = pendingReadings.any { reading ->
            reading.subscriberId == subscriber.id && isDateInCycle(reading.readingDate, cycleRange)
        }
        if (hasPendingInCycle) return true

        return isDateInCycle(subscriber.lastReadingDate, cycleRange)
    }
}
