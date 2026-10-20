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

    private val _profileImagePath = MutableStateFlow(repository.getProfileImagePath())
    val profileImagePath: StateFlow<String?> = _profileImagePath.asStateFlow()

    fun saveProfileImagePath(path: String) {
        repository.setProfileImagePath(path)
        _profileImagePath.value = path
    }

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

    private val _sessionRevokedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionRevokedEvents: SharedFlow<Unit> = _sessionRevokedEvents.asSharedFlow()

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

    val currentCycleRange: StateFlow<CycleDateRange> = combine(
        _readingCycleType,
        _weeklyCycleStartDay
    ) { type: ReadingCycleType, day: Int ->
        ReadingCycleCalculator.calculateCurrentCycleRange(type, day)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReadingCycleCalculator.calculateCurrentCycleRange(_readingCycleType.value, _weeklyCycleStartDay.value)
    )

    // Subscriber Cycle Filter (All, Unread this cycle, Read this cycle)
    private val _cycleFilter = MutableStateFlow(SubscriberCycleFilter.ALL)
    val cycleFilter: StateFlow<SubscriberCycleFilter> = _cycleFilter.asStateFlow()

    // Subscriber Sorting Mode
    private val _sortOrder = MutableStateFlow(SubscriberSortOrder.DEFAULT)
    val sortOrder: StateFlow<SubscriberSortOrder> = _sortOrder.asStateFlow()

    // Current Device Location for proximity sorting
    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    fun setSortOrder(order: SubscriberSortOrder) {
        _sortOrder.value = order
    }

    fun updateUserLocation(lat: Double, lng: Double) {
        _currentLocation.value = lat to lng
    }

    fun setCycleFilter(filter: SubscriberCycleFilter) {
        _cycleFilter.value = filter
    }

    fun refreshReadingCycleConfiguration() {
        if (!_isLoggedIn.value) return
        viewModelScope.launch {
            repository.refreshReadingCycleConfiguration().onSuccess {
                _readingCycleType.value = repository.getReadingCycleType()
                _weeklyCycleStartDay.value = repository.getWeeklyCycleStartDay()
            }
        }
    }

    fun setReadingCycleType(type: ReadingCycleType) {
        repository.setReadingCycleType(type)
        _readingCycleType.value = type
    }

    fun setWeeklyCycleStartDay(day: Int) {
        repository.setWeeklyCycleStartDay(day)
        _weeklyCycleStartDay.value = day
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

    // Filtered & Sorted Subscribers (Arabic Search, Cycle Filter, GPS Proximity & History Order)
    val filteredSubscribers: StateFlow<List<SubscriberEntity>> = combine(
        subscribers,
        _searchQuery,
        _cycleFilter,
        _sortOrder,
        _currentLocation,
        pendingReadings,
        currentCycleRange
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val subs = args[0] as List<SubscriberEntity>
        val query = args[1] as String
        val filter = args[2] as SubscriberCycleFilter
        val sort = args[3] as SubscriberSortOrder
        @Suppress("UNCHECKED_CAST")
        val myLoc = args[4] as? Pair<Double, Double>
        @Suppress("UNCHECKED_CAST")
        val readings = args[5] as List<PendingReadingEntity>
        val cycle = args[6] as CycleDateRange

        val filtered = subs.filter { sub ->
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

        when (sort) {
            SubscriberSortOrder.DEFAULT -> filtered
            SubscriberSortOrder.PREVIOUS_INPUT_ORDER -> {
                // Sort by last reading date / timestamp descending (or most recently handled order)
                filtered.sortedByDescending { it.lastReadingDate }
            }
            SubscriberSortOrder.NEAREST_LOCATION -> {
                if (myLoc != null) {
                    filtered.sortedBy { sub ->
                        // Match with pending reading location if available, otherwise keep original order
                        val subReading = readings.firstOrNull { it.subscriberId == sub.id && it.latitude != 0.0 }
                        if (subReading != null) {
                            val dLat = Math.toRadians(subReading.latitude - myLoc.first)
                            val dLon = Math.toRadians(subReading.longitude - myLoc.second)
                            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                                    Math.cos(Math.toRadians(myLoc.first)) * Math.cos(Math.toRadians(subReading.latitude)) *
                                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
                            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                            6371000.0 * c
                        } else {
                            Double.MAX_VALUE
                        }
                    }
                } else filtered
            }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getNextUnreadSubscriber(currentSubscriberId: String): SubscriberEntity? {
        val currentList = filteredSubscribers.value
        val unreadList = currentList.filter { !isSubscriberReadInCurrentCycle(it) }
        val currentIndexInUnread = unreadList.indexOfFirst { it.id == currentSubscriberId }
        return if (currentIndexInUnread in 0 until unreadList.size - 1) {
            unreadList[currentIndexInUnread + 1]
        } else {
            unreadList.firstOrNull { it.id != currentSubscriberId }
        }
    }

    // Synchronization States
    private val _syncProgress = MutableStateFlow(0)
    val syncProgress: StateFlow<Int> = _syncProgress.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow("")
    val syncStatusMessage: StateFlow<String> = _syncStatusMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // This reflects actual validated internet access, not merely a connected Wi-Fi/mobile network.
    private fun hasValidatedInternet(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private val _isOnline = MutableStateFlow(hasValidatedInternet())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private fun refreshConnectivityState() {
        _isOnline.value = hasValidatedInternet()
    }

    init {
        // Revoke UI access when the server confirms this device's session is no longer valid.
        // This event is raised only after the access token receives 401 and refresh is rejected;
        // network failures do not log the technician out.
        viewModelScope.launch {
            repository.sessionRevokedEvents.collect {
                _isLoggedIn.value = false
                _mustChangePassword.value = false
                _loginError.value = "تم إنهاء جلسة هذا الجهاز أو انتهت صلاحيتها، وقد يكون الحساب قد سُجّل الدخول من جهاز آخر. القراءات غير المتزامنة ما زالت محفوظة محلياً؛ سجّل الدخول مجدداً لمتابعة المزامنة."
                _sessionRevokedEvents.emit(Unit)
            }
        }

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
                        refreshConnectivityState()
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        refreshConnectivityState()
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (hasInternet) {
                            viewModelScope.launch {
                                val pending = pendingReadings.value.filter { it.status == "pending" || it.status == "retryable" }
                                if (pending.isNotEmpty() && !_isSyncing.value) syncAllData()
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        // Re-check the active default network so switching between Wi-Fi and mobile data
                        // does not briefly report offline when another validated network is available.
                        refreshConnectivityState()
                    }

                    override fun onUnavailable() {
                        _isOnline.value = false
                    }
                }
                networkCallback = callback
                cm.registerDefaultNetworkCallback(callback)
                refreshConnectivityState()
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
                _profileImagePath.value = repository.getProfileImagePath()
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
        readingValueText: String? = null,
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
                fieldVerified = fieldVerified,
                readingValueText = readingValueText
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

    private val _serverSyncedCycleCount = MutableStateFlow<Int?>(null)
    val serverSyncedCycleCount: StateFlow<Int?> = _serverSyncedCycleCount.asStateFlow()

    private val _cycleSyncStatsError = MutableStateFlow<String?>(null)
    val cycleSyncStatsError: StateFlow<String?> = _cycleSyncStatsError.asStateFlow()

    fun refreshCycleSyncStats() {
        if (!_isLoggedIn.value) return
        val range = currentCycleRange.value
        viewModelScope.launch {
            _cycleSyncStatsError.value = null
            val result = repository.getCycleSyncedReadingCount(range.startMillis, range.endMillis)
            if (result.isSuccess) {
                _serverSyncedCycleCount.value = result.getOrNull()
            } else {
                _serverSyncedCycleCount.value = null
                _cycleSyncStatsError.value = result.exceptionOrNull()?.message
                    ?: "تعذر تحميل إحصاءات مزامنة الدورة من الخادم."
            }
        }
    }

    fun refreshCurrentCycleData() {
        if (!_isLoggedIn.value || _isSyncing.value) return
        val range = currentCycleRange.value
        viewModelScope.launch {
            // Run both refreshes sequentially to avoid concurrent token refresh races.
            _isSyncing.value = true
            _syncStatusMessage.value = "جاري تحديث بيانات دورة القراءات..."
            val subscribersResult = repository.refreshSubscribers()
            _cycleSyncStatsError.value = null
            val statsResult = repository.getCycleSyncedReadingCount(range.startMillis, range.endMillis)
            if (statsResult.isSuccess) {
                _serverSyncedCycleCount.value = statsResult.getOrNull()
            } else {
                _serverSyncedCycleCount.value = null
                _cycleSyncStatsError.value = statsResult.exceptionOrNull()?.message
                    ?: "تعذر تحميل إحصاءات مزامنة الدورة من الخادم."
            }
            _isSyncing.value = false
            _syncStatusMessage.value = when {
                subscribersResult.isFailure -> "تعذر تحديث قائمة المشتركين: ${subscribersResult.exceptionOrNull()?.message}"
                statsResult.isFailure -> "تم تحديث المشتركين، لكن تعذر تحديث إحصاءات المزامنة."
                else -> "تم تحديث بيانات الدورة من الخادم."
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
                refreshCycleSyncStats()
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
