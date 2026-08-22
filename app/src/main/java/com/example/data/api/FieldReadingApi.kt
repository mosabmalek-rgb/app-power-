package com.example.data.api

import retrofit2.http.*
import okhttp3.MultipartBody

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(val token: String, val refreshToken: String, val user: UserProfile)
data class UserProfile(
    val id: String, 
    val name: String, 
    val username: String, 
    val role: String, 
    val branchPermissions: List<String>,
    val companyName: String? = null
)

data class SubscriberDto(
    val id: String,
    val refNumber: String,
    val name: String,
    val phone: String,
    val address: String,
    val branchId: String,
    val lastReading: Double,
    val lastReadingDate: String,
    val balance: Double
)

data class ReadingUploadDto(
    val subscriberId: String,
    val currentReading: Double,
    val readingDate: String,
    val notes: String,
    val latitude: Double,
    val longitude: Double,
    val photoUrl: String?,
    val isMeterReplacement: Boolean
)

data class PaymentUploadDto(
    val subscriberId: String,
    val amount: Double,
    val paymentMethod: String,
    val notes: String
)

data class BulkSyncRequest(
    val readings: List<ReadingUploadDto>,
    val payments: List<PaymentUploadDto>
)

data class BulkSyncResponse(
    val success: Boolean,
    val syncedReadingCount: Int,
    val syncedPaymentCount: Int,
    val message: String
)

data class UploadResponse(
    val photoUrl: String
)

interface FieldReadingApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun getProfile(): UserProfile

    @GET("api/field/subscribers")
    suspend fun getSubscribers(): List<SubscriberDto>

    @POST("api/field/readings/bulk")
    suspend fun syncBulk(@Body request: BulkSyncRequest): BulkSyncResponse

    @Multipart
    @POST("api/field/upload")
    suspend fun uploadPhoto(@Part photo: MultipartBody.Part): UploadResponse

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body refreshToken: String): LoginResponse
}
