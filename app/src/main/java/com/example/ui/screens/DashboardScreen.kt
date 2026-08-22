package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: FieldReadingViewModel,
    onNavigateToSubscribers: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val userName by viewModel.userName.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val companyName by viewModel.companyName.collectAsState()
    val pendingReadings by viewModel.pendingReadings.collectAsState()
    val subscribers by viewModel.subscribers.collectAsState()

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
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Home (Active)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { /* Already here */ }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "الرئيسية",
                            tint = Color(0xFF004A77),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "الرئيسية",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF004A77)
                        )
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
                            modifier = Modifier.size(24.dp)
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
                                modifier = Modifier.size(24.dp)
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
                                    .padding(end = 12.dp, top = 2.dp)
                                    .size(16.dp)
                                    .background(Color(0xFFE28A00), CircleShape)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = totalPending.toString(),
                                    fontSize = 9.sp,
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
                            modifier = Modifier.size(24.dp)
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

                    // Grid in Header Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "قراءات اليوم",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    readingsTodayCount.toString(),
                                    fontSize = 24.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Card 2
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0x33FFB300), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0x33FFB300), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    "معلقة للمزامنة",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFFD54F),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    totalPending.toString(),
                                    fontSize = 24.sp,
                                    color = Color(0xFFFFF9C4),
                                    fontWeight = FontWeight.Bold
                                )
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Interactive search bar card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSubscribers() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Simulated Text Field
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF7F9FC), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "بحث باسم المشترك أو الرقم المرجعي...",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Large CTA button
                        Button(
                            onClick = onNavigateToSubscribers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_new_reading_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF004A77)
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("إدخال قراءة جديدة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

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
                    val allLocalPending = pendingReadings + pendingPayments
                    
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
                                    text = "لا توجد أي عمليات فنية أو دفعات معلقة حالياً.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        // Take last 5 operations
                        allLocalPending.takeLast(5).reversed().forEach { op ->
                            val opSubId = when (op) {
                                is PendingReadingEntity -> op.subscriberId
                                is PendingPaymentEntity -> op.subscriberId
                                else -> ""
                            }
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
