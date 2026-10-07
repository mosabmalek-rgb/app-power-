package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FieldReadingViewModel
import com.example.data.PendingReadingEntity
import com.example.data.PendingPaymentEntity
import com.example.data.ReadingCycleCalculator
import com.example.data.SubscriberCycleFilter
import com.example.data.SubscriberEntity

enum class DashboardTab {
    HOME,
    CYCLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FieldReadingViewModel,
    onNavigateToSubscribers: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var currentTab by remember { mutableStateOf(DashboardTab.HOME) }
    var cycleSubscribersFilter by remember { mutableStateOf(SubscriberCycleFilter.UNREAD_THIS_CYCLE) }

    val userName by viewModel.userName.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val companyName by viewModel.companyName.collectAsState()
    val pendingReadings by viewModel.pendingReadings.collectAsState()
    val subscribers by viewModel.subscribers.collectAsState()
    val unreadThisCycleCount by viewModel.unreadSubscribersCount.collectAsState()
    val readThisCycleCount by viewModel.readSubscribersCount.collectAsState()
    val currentCycleRange by viewModel.currentCycleRange.collectAsState()

    val pendingReadingsCount = pendingReadings.filter { it.status == "pending" }.size
    val totalPending = pendingReadingsCount

    // Calculate readings done today
    val todayDateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    val readingsTodayCount = pendingReadings.filter { it.readingDate.startsWith(todayDateString) }.size

    Scaffold(
        bottomBar = {
            // High Density custom bottom navigation bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentTab = DashboardTab.HOME }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "الرئيسية",
                            tint = if (currentTab == DashboardTab.HOME) Color(0xFF004A77) else Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "الرئيسية",
                            fontSize = 10.sp,
                            fontWeight = if (currentTab == DashboardTab.HOME) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentTab == DashboardTab.HOME) Color(0xFF004A77) else Color.Gray
                        )
                    }

                    // Reading Cycle Tab (تبويب الدورة الحالية)
                    Box(
                        contentAlignment = Alignment.TopEnd,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { currentTab = DashboardTab.CYCLE }
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "الدورة",
                                tint = if (currentTab == DashboardTab.CYCLE) Color(0xFF004A77) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "الدورة",
                                fontSize = 10.sp,
                                fontWeight = if (currentTab == DashboardTab.CYCLE) FontWeight.Bold else FontWeight.Normal,
                                color = if (currentTab == DashboardTab.CYCLE) Color(0xFF004A77) else Color.Gray
                            )
                        }
                        if (unreadThisCycleCount > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp, top = 1.dp)
                                    .size(16.dp)
                                    .background(Color(0xFFEA580C), CircleShape)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (unreadThisCycleCount > 99) "99+" else unreadThisCycleCount.toString(),
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Subscribers
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToSubscribers() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = "المشتركين",
                            tint = Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "المشتركين",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }

                    // Sync
                    Box(
                        contentAlignment = Alignment.TopEnd,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToSync() }
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Sync,
                                contentDescription = "المزامنة",
                                tint = Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                "المزامنة",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        if (totalPending > 0) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp, top = 1.dp)
                                    .size(16.dp)
                                    .background(Color(0xFFE28A00), CircleShape)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = totalPending.toString(),
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Settings
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToSettings() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "الإعدادات",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (currentTab == DashboardTab.CYCLE) {
            BackHandler { currentTab = DashboardTab.HOME }
            ReadingCycleTabContent(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateToSubscribers = onNavigateToSubscribers,
                onNavigateToSettings = onNavigateToSettings
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .background(Color(0xFFF7F9FC))
                    .verticalScroll(rememberScrollState())
            ) {
            // High Density Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF004A77),
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column {
                    // Header row: Profile & connectivity badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar Icon container
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFF00629D), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userName.firstOrNull()?.toString() ?: "م",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "مرحباً، $userName",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "الدور الميداني: $userRole",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                                Text(
                                    text = "المنشأة: $companyName",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                        }

                        // Connectivity badge matching tailwind style
                        val statusBg = if (totalPending > 0) Color(0x33FF5252) else Color(0x334CAF50)
                        val statusText = if (totalPending > 0) "غير متصل" else "متصل"
                        val statusBorder = if (totalPending > 0) Color(0x66FF5252) else Color(0x664CAF50)
                        val statusDotColor = if (totalPending > 0) Color(0xFFFF5252) else Color(0xFF4CAF50)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(statusBg, RoundedCornerShape(12.dp))
                                .border(1.dp, statusBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusDotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusText,
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid in Header Banner (2x2 Clean Metrics Grid)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Total Existing Subscribers Card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        viewModel.setCycleFilter(SubscriberCycleFilter.ALL)
                                        onNavigateToSubscribers()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "إجمالي المشتركين",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.People,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${subscribers.size}",
                                        fontSize = 22.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "مشترك مسجل",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Unread This Cycle Card (Attention Highlight)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0x33FF9800), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color(0x66FFB74D), RoundedCornerShape(14.dp))
                                    .clickable {
                                        viewModel.setCycleFilter(SubscriberCycleFilter.UNREAD_THIS_CYCLE)
                                        onNavigateToSubscribers()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "لم تُسجل للدورة",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFFCC80),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.HourglassTop,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$unreadThisCycleCount",
                                        fontSize = 22.sp,
                                        color = Color(0xFFFFF3E0),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "اضغط لإظهارهم",
                                        fontSize = 9.sp,
                                        color = Color(0xFFFFE0B2),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Completed in this cycle
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color(0x2E4CAF50), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color(0x5581C784), RoundedCornerShape(14.dp))
                                    .clickable {
                                        viewModel.setCycleFilter(SubscriberCycleFilter.READ_THIS_CYCLE)
                                        onNavigateToSubscribers()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "تمت قراءتهم",
                                            fontSize = 11.sp,
                                            color = Color(0xFFA5D6A7),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF81C784),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$readThisCycleCount",
                                        fontSize = 22.sp,
                                        color = Color(0xFFE8F5E9),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "مكتملة للدورة",
                                        fontSize = 9.sp,
                                        color = Color(0xFFC8E6C9)
                                    )
                                }
                            }

                            // Pending sync
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                    .clickable { onNavigateToSync() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "معلقة للمزامنة",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "$totalPending",
                                        fontSize = 22.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "محفوظة محلياً",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.55f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Contents Area - Offset negative margin slightly up
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset(y = (-16).dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Recent Operations header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "العمليات الأخيرة (المحلية)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Text(
                        "عرض الكل",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF004A77),
                        modifier = Modifier.clickable { onNavigateToSubscribers() }
                    )
                }

                // Dense operations list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val allLocalPending = pendingReadings
                    
                    if (allLocalPending.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد أي قراءات ميدانية معلقة حالياً.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        // Take last 5 operations
                        allLocalPending.takeLast(5).reversed().forEach { op ->
                            val opSubId = op.subscriberId
                            // Look up subscriber info if we have list
                            val subInfo = subscribers.find { it.id == opSubId }
                            val subName = subInfo?.name ?: "مشترك رقم $opSubId"
                            val refNum = subInfo?.refNumber ?: "N/A"

                            // Render item matching tailwind dense aesthetic
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToSubscribers() },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Left numeric code (like 103 blue box)
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFFF0F6FA), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = opSubId.takeLast(3),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF004A77)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = subName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1E293B)
                                            )
                                            Text(
                                                text = "مرجع: $refNum | حالة: معلقة",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Surface(
                                            color = Color(0xFFFFF3CD),
                                            shape = RoundedCornerShape(4.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFEBAA))
                                        ) {
                                            Text(
                                                text = "انتظار",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF856404),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "نشط محلياً",
                                            fontSize = 9.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
}

@Composable
fun ReadingCycleTabContent(
    innerPadding: PaddingValues,
    viewModel: FieldReadingViewModel,
    onNavigateToSubscribers: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val subscribers by viewModel.subscribers.collectAsState()
    val pendingReadings by viewModel.pendingReadings.collectAsState()
    val unreadThisCycleCount by viewModel.unreadSubscribersCount.collectAsState()
    val readThisCycleCount by viewModel.readSubscribersCount.collectAsState()
    val currentCycleRange by viewModel.currentCycleRange.collectAsState()

    var selectedFilter by remember { mutableStateOf(SubscriberCycleFilter.UNREAD_THIS_CYCLE) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(subscribers, pendingReadings, currentCycleRange, selectedFilter, searchQuery) {
        subscribers.filter { sub ->
            val matchesFilter = when (selectedFilter) {
                SubscriberCycleFilter.ALL -> true
                SubscriberCycleFilter.UNREAD_THIS_CYCLE -> !ReadingCycleCalculator.isSubscriberReadInCycle(sub, pendingReadings, currentCycleRange)
                SubscriberCycleFilter.READ_THIS_CYCLE -> ReadingCycleCalculator.isSubscriberReadInCycle(sub, pendingReadings, currentCycleRange)
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                sub.name.contains(searchQuery, ignoreCase = true) ||
                sub.refNumber.contains(searchQuery, ignoreCase = true) ||
                sub.phone.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }

    val progress = if (subscribers.isNotEmpty()) {
        (readThisCycleCount.toFloat() / subscribers.size.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
            .background(Color(0xFFF7F9FC))
    ) {
        // Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF004A77),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "بطاقة الدورة الحالية للقراءات",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentCycleRange.labelAr,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Settings Button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "ضبط دورة القراءات",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main Cycle Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "حالة إنجاز الدورة الحالية",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "النطاق: ${currentCycleRange.shortRangeText}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Surface(
                                color = if (unreadThisCycleCount == 0) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (unreadThisCycleCount == 0) Color(0xFFBBF7D0) else Color(0xFFFDE68A))
                            ) {
                                Text(
                                    text = "${(progress * 100).toInt()}% منجز",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unreadThisCycleCount == 0) Color(0xFF15803D) else Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Color(0xFF004A77),
                            trackColor = Color(0xFFE2E8F0)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3 Statistics Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Total
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("الإجمالي", fontSize = 10.sp, color = Color(0xFF64748B))
                                    Text("${subscribers.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                }
                            }

                            // Read
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF0FDF4),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("تمت القراءة", fontSize = 10.sp, color = Color(0xFF15803D))
                                    Text("$readThisCycleCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                }
                            }

                            // Unread
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFFBEB),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("بانتظار القراءة", fontSize = 10.sp, color = Color(0xFFB45309))
                                    Text("$unreadThisCycleCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar inside Cycle Tab
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث في مشتركي الدورة...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF004A77)
                    ),
                    singleLine = true
                )
            }

            // Filter Chips Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == SubscriberCycleFilter.UNREAD_THIS_CYCLE,
                        onClick = { selectedFilter = SubscriberCycleFilter.UNREAD_THIS_CYCLE },
                        label = { Text("المتبقين ($unreadThisCycleCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedFilter == SubscriberCycleFilter.READ_THIS_CYCLE,
                        onClick = { selectedFilter = SubscriberCycleFilter.READ_THIS_CYCLE },
                        label = { Text("تمت قراءتهم ($readThisCycleCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedFilter == SubscriberCycleFilter.ALL,
                        onClick = { selectedFilter = SubscriberCycleFilter.ALL },
                        label = { Text("الكل (${subscribers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Subscribers List Items
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedFilter == SubscriberCycleFilter.UNREAD_THIS_CYCLE) "لا يوجد مشتركون بانتظار القراءة في هذه الدورة! 🎉" else "لا توجد نتائج مطابقة.",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { subscriber ->
                    val isRead = ReadingCycleCalculator.isSubscriberReadInCycle(subscriber, pendingReadings, currentCycleRange)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectSubscriber(subscriber.id)
                                onNavigateToSubscribers()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subscriber.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "المرجع: ${subscriber.refNumber}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "آخر قراءة: ${subscriber.lastReading}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Surface(
                                color = if (isRead) Color(0xFFF0FDF4) else Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isRead) Color(0xFFBBF7D0) else Color(0xFFFDE68A))
                            ) {
                                Text(
                                    text = if (isRead) "تمت القراءة" else "بانتظار القراءة",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRead) Color(0xFF15803D) else Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
