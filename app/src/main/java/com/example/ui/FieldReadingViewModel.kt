package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.repository.FieldReadingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FieldReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FieldReadingDatabase.getDatabase(application)
    private val repository = FieldReadingRepository(application, db.dao())

    // UI Session State
    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userName = MutableStateFlow(repository.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userRole = MutableStateFlow(repository.getUserRole())
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _companyName = MutableStateFlow(repository.getCompanyName())
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    private val _savedUsername = MutableStateFlow(repository.getSavedUsername())
    val savedUsername: StateFlow<String> = _savedUsername.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // Database content flows
    val subscribers: StateFlow<List<SubscriberEntity>> = repository.subscribersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingReadings: StateFlow<List<PendingReadingEntity>> = repository.pendingReadingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingPayments: StateFlow<List<PendingPaymentEntity>> = repository.pendingPaymentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search Query Flow
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected Subscriber for details screen
    private val _selectedSubscriberId = MutableStateFlow<String?>(null)
    val selectedSubscriber: StateFlow<SubscriberEntity?> = _selectedSubscriberId
        .flatMapLatest { id ->
            if (id != null) repository.getSubscriberFlow(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Filtered Subscribers (Arabic Search & Branch Filter)
    val filteredSubscribers: StateFlow<List<SubscriberEntity>> = combine(
        subscribers,
        _searchQuery
    ) { subs, query ->
        if (query.isBlank()) {
            subs
        } else {
            subs.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.refNumber.contains(query, ignoreCase = true) ||
                it.phone.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true)
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Synchronization States
    private val _syncProgress = MutableStateFlow(0)
    val syncProgress: StateFlow<Int> = _syncProgress.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        // Auto-refresh/populate subscriber list if database is empty on start
        viewModelScope.launch {
            subscribers.first { true } // wait for first emission
            if (subscribers.value.isEmpty()) {
                repository.refreshSubscribers()
            }
        }
    }

    fun selectSubscriber(id: String?) {
        _selectedSubscriberId.value = id
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun login(username: String, password: String, remember: Boolean, onResult: (Boolean) -> Unit) {
        _loginError.value = null
        if (username.isBlank() || password.isBlank()) {
            _loginError.value = "الرجاء إدخال اسم المستخدم وكلمة المرور"
            onResult(false)
            return
        }
        viewModelScope.launch {
            val result = repository.login(username, password, remember)
            if (result.isSuccess) {
                _isLoggedIn.value = true
                _userName.value = repository.getUserName()
                _userRole.value = repository.getUserRole()
                _companyName.value = repository.getCompanyName()
                _savedUsername.value = repository.getSavedUsername()
                repository.refreshSubscribers()
                onResult(true)
            } else {
                _loginError.value = result.exceptionOrNull()?.message ?: "تعذر تسجيل الدخول إلى PowerHub"
                onResult(false)
            }
        }
    }

    fun logout() {
        repository.clearSession()
        _isLoggedIn.value = false
    }

    fun saveReading(
        subscriberId: String,
        currentReading: Double,
        notes: String,
        latitude: Double,
        longitude: Double,
        photoPath: String?,
        isMeterReplacement: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.saveReading(
                subscriberId = subscriberId,
                currentReading = currentReading,
                notes = notes,
                latitude = latitude,
                longitude = longitude,
                photoPath = photoPath,
                isMeterReplacement = isMeterReplacement
            )
            if (result.isSuccess) {
                FieldReadingSyncWorker.enqueue(getApplication())
                onResult(true, "تم حفظ القراءة محلياً بنجاح (حالة معلقة)")
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "خطأ في حفظ القراءة")
            }
        }
    }

    fun savePayment(
        subscriberId: String,
        amount: Double,
        paymentMethod: String,
        notes: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.savePayment(
                subscriberId = subscriberId,
                amount = amount,
                paymentMethod = paymentMethod,
                notes = notes
            )
            if (success) {
                onResult(true, "تم حفظ الدفعة المالية محلياً بنجاح")
            } else {
                onResult(false, "الرجاء إدخال قيمة دفعة صالحة أكبر من الصفر")
            }
        }
    }

    fun refreshSubscribers() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatusMessage.value = "جاري سحب بيانات المشتركين..."
            val result = repository.refreshSubscribers()
            _isSyncing.value = false
            if (result.isSuccess) {
                _syncStatusMessage.value = "تم تحديث المشتركين بنجاح"
            } else {
                _syncStatusMessage.value = "فشل تحديث المشتركين: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun syncAllData() {
        if (_isSyncing.value) return
        
        viewModelScope.launch {
            _isSyncing.value = true
            _syncProgress.value = 0
            _syncStatusMessage.value = "جاري تهيئة المزامنة الميدانية..."
            
            val result = repository.syncAllData { progress, message ->
                _syncProgress.value = progress
                _syncStatusMessage.value = message
            }
            
            _isSyncing.value = false
            if (result.isSuccess) {
                _syncStatusMessage.value = result.getOrNull() ?: "اكتملت المزامنة بنجاح"
            } else {
                _syncStatusMessage.value = "فشلت المزامنة: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
