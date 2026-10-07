package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.repository.FieldReadingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FieldReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FieldReadingDatabase.getDatabase(application)
    private val repository = FieldReadingRepository(application, db.dao())

    init {
        FieldReadingSyncWorker.schedulePeriodic(application)
    }

    // UI Session State
    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    private val _mustChangePassword = MutableStateFlow(repository.mustChangePassword())
    val mustChangePassword: StateFlow<Boolean> = _mustChangePassword.asStateFlow()

    private val _userName = MutableStateFlow(repository.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userRole = MutableStateFlow(repository.getUserRole())
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val _companyName = MutableStateFlow(repository.getCompanyName())
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    private val _savedUsername = MutableStateFlow(repository.getSavedUsername())
    val savedUsername: StateFlow<String> = _savedUsername.asStateFlow()

    fun hasLocalPin(): Boolean = repository.hasLocalPin()
    fun isSubscriptionValidOffline(): Boolean = repository.isSubscriptionValidOffline()
    fun setLocalPin(pin: String): Boolean = runCatching { repository.setLocalPin(pin); true }.getOrDefault(false)
    fun verifyLocalPin(pin: String): Boolean = repository.verifyLocalPin(pin)

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

    // Reading Cycle Configuration
    private val _readingCycleType = MutableStateFlow<ReadingCycleType>(repository.getReadingCycleType())
    val readingCycleType: StateFlow<ReadingCycleType> = _readingCycleType.asStateFlow()

    private val _weeklyCycleStartDay = MutableStateFlow<Int>(repository.getWeeklyCycleStartDay())
    val weeklyCycleStartDay: StateFlow<Int> = _weeklyCycleStartDay.asStateFlow()
    private val _monthlyStartDay = MutableStateFlow<Int>(repository.getMonthlyStartDay())
    val monthlyStartDay: StateFlow<Int> = _monthlyStartDay.asStateFlow()

    val currentCycleRange: StateFlow<CycleDateRange> = combine(
        _readingCycleType,
        _weeklyCycleStartDay,
        _monthlyStartDay
    ) { type: ReadingCycleType, day: Int, monthDay: Int ->
        ReadingCycleCalculator.calculateCurrentCycleRange(type, day, monthDay)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReadingCycleCalculator.calculateCurrentCycleRange(_readingCycleType.value, _weeklyCycleStartDay.value, _monthlyStartDay.value)
    )

    // Subscriber Cycle Filter (All, Unread this cycle, Read this cycle)
    private val _cycleFilter = MutableStateFlow(SubscriberCycleFilter.ALL)
    val cycleFilter: StateFlow<SubscriberCycleFilter> = _cycleFilter.asStateFlow()

    fun setCycleFilter(filter: SubscriberCycleFilter) {
        _cycleFilter.value = filter
    }

    fun setReadingCycleType(type: ReadingCycleType) {
        repository.setReadingCycleType(type)
        _readingCycleType.value = type
    }

    fun setWeeklyCycleStartDay(day: Int) {
        repository.setWeeklyCycleStartDay(day)
        _weeklyCycleStartDay.value = day
    }

    fun refreshReadingCycle() {
        viewModelScope.launch {
            repository.refreshReadingCycle().onSuccess { config ->
                _readingCycleType.value = config.first
                _weeklyCycleStartDay.value = config.second
                _monthlyStartDay.value = config.third
            }
        }
    }

    // Unread & Read Subscribers in Current Cycle Counts
    val unreadSubscribersCount: StateFlow<Int> = combine(
        subscribers,
        pendingReadings,
        currentCycleRange
    ) { subs, readings, cycle ->
        subs.count { !ReadingCycleCalculator.isSubscriberReadInCycle(it, readings, cycle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val readSubscribersCount: StateFlow<Int> = combine(
        subscribers,
        pendingReadings,
        currentCycleRange
    ) { subs, readings, cycle ->
        subs.count { ReadingCycleCalculator.isSubscriberReadInCycle(it, readings, cycle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun isSubscriberReadInCurrentCycle(subscriber: SubscriberEntity): Boolean {
        return ReadingCycleCalculator.isSubscriberReadInCycle(
            subscriber,
            pendingReadings.value,
            currentCycleRange.value
        )
    }

    // Selected Subscriber for details screen
    private val _selectedSubscriberId = MutableStateFlow<String?>(null)
    val selectedSubscriber: StateFlow<SubscriberEntity?> = _selectedSubscriberId
        .flatMapLatest { id ->
            if (id != null) repository.getSubscriberFlow(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Filtered Subscribers (Arabic Search & Cycle Filter)
    val filteredSubscribers: StateFlow<List<SubscriberEntity>> = combine(
        subscribers,
        _searchQuery,
        _cycleFilter,
        pendingReadings,
        currentCycleRange
    ) { subs, query, filter, readings, cycle ->
        subs.filter { sub ->
            val matchesQuery = if (query.isBlank()) true else {
                sub.name.contains(query, ignoreCase = true) ||
                sub.refNumber.contains(query, ignoreCase = true) ||
                sub.phone.contains(query, ignoreCase = true) ||
                sub.address.contains(query, ignoreCase = true)
            }
            val matchesFilter = when (filter) {
                SubscriberCycleFilter.ALL -> true
                SubscriberCycleFilter.UNREAD_THIS_CYCLE -> !ReadingCycleCalculator.isSubscriberReadInCycle(sub, readings, cycle)
                SubscriberCycleFilter.READ_THIS_CYCLE -> ReadingCycleCalculator.isSubscriberReadInCycle(sub, readings, cycle)
            }
            matchesQuery && matchesFilter
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

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        // Auto-refresh/populate subscriber list if database is empty on start
        viewModelScope.launch {
            subscribers.first { true } // wait for first emission
            if (subscribers.value.isEmpty()) {
                repository.refreshSubscribers()
            }
        }

        // Automatic synchronization upon internet connection
        runCatching {
            val cm = connectivityManager
            if (cm != null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        viewModelScope.launch {
                            // Check if we have pending items to sync
                            val pending = pendingReadings.value.filter { it.status == "pending" }
                            if (pending.isNotEmpty() && !_isSyncing.value) {
                                syncAllData()
                            }
                        }
                    }
                }
                networkCallback = callback
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, callback)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
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
                _mustChangePassword.value = repository.mustChangePassword()
                if (!_mustChangePassword.value) repository.refreshSubscribers()
                onResult(true)
            } else {
                _loginError.value = result.exceptionOrNull()?.message ?: "تعذر تسجيل الدخول إلى PowerHub"
                onResult(false)
            }
        }
    }

    fun changeTemporaryPassword(newPassword: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = repository.changeTemporaryPassword(newPassword)
            if (result.isSuccess) {
                _mustChangePassword.value = false
                onResult(true, null)
            } else onResult(false, result.exceptionOrNull()?.message ?: "تعذر تغيير كلمة المرور.")
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout(); _isLoggedIn.value = false; _mustChangePassword.value = false }
    }

    fun saveReading(
        subscriberId: String,
        currentReading: Double,
        notes: String,
        latitude: Double,
        longitude: Double,
        photoPath: String?,
        isReadingReset: Boolean,
        fieldVerified: Boolean = true,
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
                isReadingReset = isReadingReset,
                fieldVerified = fieldVerified
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
                _syncStatusMessage.value = result.getOrNull()?.message() ?: "اكتملت المزامنة بنجاح"
            } else {
                _syncStatusMessage.value = "فشلت المزامنة: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun requeueReadingForReview(localId: Int) {
        viewModelScope.launch {
            repository.requeueReadingForReview(localId)
            _syncStatusMessage.value = "أُعيدت القراءة إلى طابور المزامنة. راجع بياناتها ثم ابدأ المزامنة عند توفر الإنترنت."
            FieldReadingSyncWorker.enqueue(getApplication())
        }
    }
}
