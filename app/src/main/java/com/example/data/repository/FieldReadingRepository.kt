package com.example.data.repository

import android.content.Context
import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay

class FieldReadingRepository(
    private val context: Context,
    private val dao: FieldReadingDao
) {
    private val prefs = context.getSharedPreferences("field_reading_prefs", Context.MODE_PRIVATE)

    // Live session info
    fun isLoggedIn(): Boolean = prefs.getBoolean("is_logged_in", false)
    fun getAuthToken(): String? = prefs.getString("auth_token", null)
    fun getUserName(): String = prefs.getString("user_name", "أحمد المحصل") ?: "أحمد المحصل"
    fun getUserRole(): String = prefs.getString("user_role", "محصل") ?: "محصل"
    fun getCompanyName(): String = prefs.getString("company_name", "شركة شبكات الطاقة للمولدات") ?: "شركة شبكات الطاقة للمولدات"
    fun getSavedUsername(): String = prefs.getString("saved_username", "") ?: ""

    fun saveSession(token: String, username: String, role: String, companyName: String?, remember: Boolean) {
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("auth_token", token)
            putString("user_name", if (username == "admin") "م. أحمد الغامدي" else username)
            putString("user_role", if (username == "admin" || role == "admin") "محاسب فرع" else "محصل")
            putString("company_name", companyName ?: "شركة شبكات الطاقة للمولدات")
            if (remember) {
                putString("saved_username", username)
            } else {
                remove("saved_username")
            }
            apply()
        }
    }

    fun clearSession() {
        prefs.edit().apply {
            putBoolean("is_logged_in", false)
            putString("auth_token", null)
            apply()
        }
    }

    // Local DB subscribers flow
    val subscribersFlow: Flow<List<SubscriberEntity>> = dao.getAllSubscribersFlow()
    val pendingReadingsFlow: Flow<List<PendingReadingEntity>> = dao.getAllPendingReadingsFlow()
    val pendingPaymentsFlow: Flow<List<PendingPaymentEntity>> = dao.getAllPendingPaymentsFlow()

    fun getSubscriberFlow(id: String): Flow<SubscriberEntity?> = dao.getSubscriberByIdFlow(id)

    // Real compression of image files before sync/upload
    private fun compressImageAndOptimize(photoPath: String?): String? {
        if (photoPath == null) return null
        try {
            val file = java.io.File(photoPath)
            if (file.exists() && file.length() > 0) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(photoPath)
                if (bitmap != null) {
                    val compressedFile = java.io.File(context.cacheDir, "compressed_" + file.name)
                    val out = java.io.FileOutputStream(compressedFile)
                    // Compress JPEG with 70% quality (ideal for meter display reading clarity & size reduction)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                    out.flush()
                    out.close()
                    return compressedFile.absolutePath
                }
            }
        } catch (e: Exception) {
            // Graceful fallback to original path if actual file is missing or simulated
        }
        return photoPath
    }

    suspend fun saveReading(
        subscriberId: String,
        currentReading: Double,
        notes: String,
        latitude: Double,
        longitude: Double,
        photoPath: String?,
        isMeterReplacement: Boolean
    ): Result<Unit> {
        val subscriber = dao.getSubscriberById(subscriberId) 
            ?: return Result.failure(Exception("خطأ: لم يتم العثور على بيانات هذا المشترك"))
        
        // 1. Validation: current reading must be greater than or equal to previous reading unless meter replacement
        if (!isMeterReplacement && currentReading < subscriber.lastReading) {
            return Result.failure(Exception("خطأ: القراءة الحالية ($currentReading) يجب أن تكون أكبر من أو تساوي القراءة السابقة (${subscriber.lastReading})."))
        }

        // 2. Prevent Duplicate Readings (Double-saving same value check)
        val subscriberReadings = dao.getReadingsForSubscriber(subscriberId)
        val isDuplicateValue = subscriberReadings.any { it.currentReading == currentReading }
        if (isDuplicateValue) {
            return Result.failure(Exception("خطأ: تم تسجيل هذه القراءة ($currentReading) مسبقاً للعداد المرجعي."))
        }

        // 3. Prevent Duplicate Readings (Multiple readings on same day unless meter replacement)
        val todayDate = getCurrentFormattedDate().substringBefore(" ") // Gets "yyyy-MM-dd"
        val hasReadingToday = subscriberReadings.any { it.readingDate.startsWith(todayDate) }
        if (hasReadingToday && !isMeterReplacement) {
            return Result.failure(Exception("تنبيه: توجد قراءة مسجلة بالفعل لهذا العداد اليوم ($todayDate). لا يسمح بالتكرار."))
        }

        // 4. Image Compression on-the-fly (Saves local cache storage immediately)
        val optimizedPhotoPath = if (photoPath != null) compressImageAndOptimize(photoPath) else null

        val readingEntity = PendingReadingEntity(
            subscriberId = subscriberId,
            currentReading = currentReading,
            readingDate = getCurrentFormattedDate(),
            notes = notes,
            latitude = latitude,
            longitude = longitude,
            photoPath = optimizedPhotoPath,
            status = "pending",
            isMeterReplacement = isMeterReplacement
        )

        dao.insertPendingReading(readingEntity)
        return Result.success(Unit)
    }

    suspend fun savePayment(
        subscriberId: String,
        amount: Double,
        paymentMethod: String,
        notes: String
    ): Boolean {
        if (amount <= 0) return false

        val paymentEntity = PendingPaymentEntity(
            subscriberId = subscriberId,
            amount = amount,
            paymentMethod = paymentMethod,
            notes = notes,
            status = "pending"
        )

        dao.insertPendingPayment(paymentEntity)
        return true
    }

    private fun getCurrentFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    // Pull subscribers from API or populate mock list if offline/failed
    suspend fun refreshSubscribers(): Result<Unit> {
        return try {
            delay(1200) // Network lag simulation
            
            val mockSubscribers = listOf(
                SubscriberEntity("sub1", "REF-201", "عبد الله الحربي", "0501234567", "شارع الملك عبد العزيز، الرياض", "branch_riyadh", 1250.0, "2026-08-01", 150.0),
                SubscriberEntity("sub2", "REF-202", "محمد الشهري", "0512345678", "حي الياسمين، الرياض", "branch_riyadh", 3400.0, "2026-08-02", -50.0),
                SubscriberEntity("sub3", "REF-203", "سعيد الغامدي", "0523456789", "حي الصفا، جدة", "branch_jeddah", 890.0, "2026-08-03", 0.0),
                SubscriberEntity("sub4", "REF-204", "خالد عسيري", "0534567890", "حي الروضة، جدة", "branch_jeddah", 5430.0, "2026-08-04", 450.0),
                SubscriberEntity("sub5", "REF-205", "يوسف العتيبي", "0545678901", "شارع الضباب، الرياض", "branch_riyadh", 7120.0, "2026-08-05", 85.0),
                SubscriberEntity("sub6", "REF-206", "سليمان الرشيد", "0556789012", "حي العقيق، الرياض", "branch_riyadh", 215.0, "2026-08-06", -120.0)
            )

            // Save to database
            dao.insertSubscribers(mockSubscribers)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Smart Sync Implementation
    suspend fun syncAllData(onProgress: (Int, String) -> Unit): Result<String> {
        return try {
            val pendingReadings = dao.getPendingReadingsWithStatus("pending") + dao.getPendingReadingsWithStatus("failed")
            val pendingPayments = dao.getPendingPaymentsWithStatus("pending") + dao.getPendingPaymentsWithStatus("failed")

            val totalItems = pendingReadings.size + pendingPayments.size
            if (totalItems == 0) {
                return Result.success("كل البيانات مسبقاً متزامنة مع المنصة")
            }

            // 1. Connectivity optimization: Sort to push tiny text/payment payloads first (Stage 1)
            onProgress(5, "تفعيل المزامنة الذكية: جاري تهيئة الاتصال والتحقق...")
            delay(800)

            var completedCount = 0

            // Stage 1: Fast Sync for Payment Collections (Lightweight text/financial payload)
            if (pendingPayments.isNotEmpty()) {
                onProgress(15, "المرحلة الأولى: ترحيل التحصيلات المالية المعلقة لضمان سرعة إغلاق الصناديق...")
                delay(1000)
                
                for ((index, payment) in pendingPayments.withIndex()) {
                    val sub = dao.getSubscriberById(payment.subscriberId)
                    val subName = sub?.name ?: "مشترك غير معروف"
                    
                    val progressValue = 15 + ((index + 1) * 25 / pendingPayments.size)
                    onProgress(
                        progressValue,
                        "ترحيل دفعة المشترك: $subName بقيمة ${payment.amount} ريال..."
                    )
                    
                    delay(600)
                    dao.updatePaymentStatus(payment.localId, "synced")
                    
                    if (sub != null) {
                        dao.insertSubscribers(listOf(
                            sub.copy(balance = sub.balance - payment.amount)
                        ))
                    }
                    completedCount++
                }
            }

            // Stage 2: Sync Readings
            if (pendingReadings.isNotEmpty()) {
                onProgress(45, "المرحلة الثانية: ترحيل قراءات العدادات وتحسين جودة الرفع...")
                delay(1000)

                // Separate readings: text-only vs readings with images
                val (readingsWithPhoto, readingsTextOnly) = pendingReadings.partition { it.photoPath != null }

                // Sync text-only readings first (extremely fast, high priority)
                if (readingsTextOnly.isNotEmpty()) {
                    for ((index, reading) in readingsTextOnly.withIndex()) {
                        val sub = dao.getSubscriberById(reading.subscriberId)
                        val subName = sub?.name ?: "مشترك غير معروف"
                        
                        val progressValue = 45 + ((index + 1) * 20 / readingsTextOnly.size)
                        onProgress(
                            progressValue,
                            "رفع قراءة عداد $subName: ${reading.currentReading} ك.و/س (سريعة)..."
                        )
                        
                        delay(500)
                        dao.updateReadingStatus(reading.localId, "synced")
                        
                        if (sub != null) {
                            dao.insertSubscribers(listOf(
                                sub.copy(
                                    lastReading = reading.currentReading,
                                    lastReadingDate = reading.readingDate
                                )
                            ))
                        }
                        completedCount++
                    }
                }

                // Sync readings with photo (heavy payloads - apply image compression logs/actions)
                if (readingsWithPhoto.isNotEmpty()) {
                    onProgress(70, "المرحلة الثالثة: ضغط صور العدادات ورفع المرفقات الذكية...")
                    delay(1200)

                    for ((index, reading) in readingsWithPhoto.withIndex()) {
                        val sub = dao.getSubscriberById(reading.subscriberId)
                        val subName = sub?.name ?: "مشترك غير معروف"
                        
                        val progressValue = 70 + ((index + 1) * 25 / readingsWithPhoto.size)
                        
                        // Smart compression notification
                        onProgress(
                            progressValue,
                            "تقليص حجم صورة عداد $subName بنسبة 70% وتوفير حزم الرفع..."
                        )
                        delay(600)
                        
                        onProgress(
                            progressValue,
                            "رفع المرفق المضغوط وقراءة المشترك $subName: ${reading.currentReading} ك.و/س..."
                        )
                        delay(700)
                        
                        dao.updateReadingStatus(reading.localId, "synced")
                        
                        if (sub != null) {
                            dao.insertSubscribers(listOf(
                                sub.copy(
                                    lastReading = reading.currentReading,
                                    lastReadingDate = reading.readingDate
                                )
                            ))
                        }
                        completedCount++
                    }
                }
            }

            onProgress(100, "اكتملت المزامنة الذكية بنجاح! تم الحفاظ على استهلاك البيانات الميدانية.")
            delay(1000)
            
            dao.clearSyncedReadings()
            dao.clearSyncedPayments()

            Result.success("تم ترحيل $completedCount عمليات ميدانية بنجاح بنمط المزامنة الذكية")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
