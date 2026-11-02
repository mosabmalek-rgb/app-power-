package com.example.data

import java.math.BigDecimal

data class OfflineSyncIssue(val status: String, val message: String, val errorCode: String? = null)

/**
 * Keeps the exact user-entered decimal scale when it is valid, while normalizing
 * legacy numeric-only values that no longer retain their original textual scale.
 */
object ReadingValuePrecision {
    private val enteredValuePattern = Regex("^\\d+(?:\\.\\d{1,2})?$")

    fun normalizeForSync(value: Double, enteredValue: String? = null): String? {
        if (!value.isFinite() || value < 0.0) return null

        if (enteredValue != null) {
            val raw = enteredValue.trim()
            if (!enteredValuePattern.matches(raw)) return null
            val decimal = runCatching { BigDecimal(raw) }.getOrNull() ?: return null
            if (decimal.compareTo(BigDecimal.valueOf(value)) != 0) return null
            return raw
        }

        val normalized = BigDecimal.valueOf(value).stripTrailingZeros()
        if (normalized.scale().coerceAtLeast(0) > 2) return null
        return normalized.toPlainString()
    }
}

object OfflineSyncPolicy {
    fun forHttpStatus(statusCode: Int, errorCode: String? = null): OfflineSyncIssue {
        if (statusCode == 409 && errorCode == "READING_ALREADY_EXISTS_IN_CYCLE") {
            return OfflineSyncIssue(
                "needs_review",
                "رفضت المنصة القراءة لوجود قراءة أخرى للمشترك في دورة القراءة الحالية. احتفظنا بالقراءة المحلية للمراجعة الفنية.",
                errorCode
            )
        }
        return when (statusCode) {
            401, 403 -> OfflineSyncIssue("needs_review", "انتهت صلاحية الحساب أو تغيرت الصلاحيات. سجّل الدخول مجدداً ثم أعد المحاولة.", errorCode)
            400, 404, 409, 422 -> OfflineSyncIssue("needs_review", "رفض الخادم القراءة ($statusCode). راجع المشترك والقراءة ثم أعد المحاولة.", errorCode)
            else -> OfflineSyncIssue("retryable", "تعذر على الخادم معالجة القراءة ($statusCode)؛ ستُعاد المحاولة تلقائياً.", errorCode)
        }
    }

    fun forNetworkFailure(): OfflineSyncIssue = OfflineSyncIssue(
        "retryable",
        "تعذر الوصول إلى الإنترنت أو الخادم؛ ستُعاد المحاولة عند عودة الاتصال."
    )
}
