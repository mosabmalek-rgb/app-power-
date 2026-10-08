package com.example.data

enum class SubscriberSortOrder(val titleAr: String, val subtitleAr: String) {
    DEFAULT("الترتيب الافتراضي", "حسب الاسم والمرجع"),
    PREVIOUS_INPUT_ORDER("ترتيب الإدخال السابق", "اعتماد تسلسل التعبئة في الدورة الماضية"),
    NEAREST_LOCATION("الأقرب جغرافياً (GPS)", "فرز المشتركين حسب المسافة الميدانية")
}
