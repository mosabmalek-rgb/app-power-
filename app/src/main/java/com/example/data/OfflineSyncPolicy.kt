package com.example.data

data class OfflineSyncIssue(val status: String, val message: String)

object OfflineSyncPolicy {
    fun forHttpStatus(statusCode: Int): OfflineSyncIssue = when (statusCode) {
        401, 403 -> OfflineSyncIssue("needs_review", "انتهت صلاحية الحساب أو تغيرت الصلاحيات. سجّل الدخول مجدداً ثم أعد المحاولة.")
        400, 404, 409, 422 -> OfflineSyncIssue("needs_review", "رفض الخادم القراءة ($statusCode). راجع العداد والقراءة ثم أعد المحاولة.")
        else -> OfflineSyncIssue("retryable", "تعذر على الخادم معالجة القراءة ($statusCode)؛ ستُعاد المحاولة تلقائياً.")
    }

    fun forNetworkFailure(): OfflineSyncIssue = OfflineSyncIssue(
        "retryable",
        "تعذر الوصول إلى الإنترنت أو الخادم؛ ستُعاد المحاولة عند عودة الاتصال."
    )
}
