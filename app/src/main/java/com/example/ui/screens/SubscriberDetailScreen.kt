package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FieldReadingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriberDetailScreen(
    viewModel: FieldReadingViewModel,
    onNavigateToAddReading: () -> Unit,
    onNavigateToAddPayment: () -> Unit,
    onBack: () -> Unit
) {
    val subscriber by viewModel.selectedSubscriber.collectAsState()
    val pendingReadings by viewModel.pendingReadings.collectAsState()
    val pendingPayments by viewModel.pendingPayments.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        subscriber?.name ?: "تفاصيل المشترك",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        if (subscriber == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val sub = subscriber!!
            
            // Filter local logs for this subscriber
            val subReadings = pendingReadings.filter { it.subscriberId == sub.id }
            val subPayments = pendingPayments.filter { it.subscriberId == sub.id }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Subscriber Card Detail
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "بيانات المشترك الأساسية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        DetailRow(label = "الاسم بالكامل", value = sub.name)
                        DetailRow(label = "الرقم المرجعي (العداد)", value = sub.refNumber)
                        DetailRow(label = "رقم جوال العميل", value = sub.phone)
                        DetailRow(label = "العنوان الوطني", value = sub.address)
                        DetailRow(label = "رمز فرع التغطية", value = sub.branchId)
                        DetailRow(label = "آخر قراءة مقيدة", value = "${sub.lastReading} ك.و/س")
                        DetailRow(label = "تاريخ آخر قراءة", value = sub.lastReadingDate)

                        // Balance row
                        val balanceColor = if (sub.balance > 0) {
                            MaterialTheme.colorScheme.error
                        } else if (sub.balance < 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "حالة رصيد الحساب المالي",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${sub.balance} ريال",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = balanceColor
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onNavigateToAddReading,
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .testTag("action_add_reading"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("قيد قراءة جديدة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = onNavigateToAddPayment,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("action_add_payment"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحصيل مالي", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // Operations History List Header
                Text(
                    text = "سجل العمليات المحلية المعلقة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (subReadings.isEmpty() && subPayments.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "لا توجد أي قراءات أو دفعات مسجلة محلياً لهذا المشترك بعد.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    // Display Readings local logs
                    if (subReadings.isNotEmpty()) {
                        Text(
                            text = "القراءات غير المزامنة:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        subReadings.forEach { reading ->
                            HistoryItem(
                                title = "قراءة عداد: ${reading.currentReading} ك.و",
                                date = reading.readingDate,
                                status = reading.status,
                                isReading = true,
                                extra = if (reading.isMeterReplacement) "تغيير عداد" else null
                            )
                        }
                    }

                    // Display Payments local logs
                    if (subPayments.isNotEmpty()) {
                        Text(
                            text = "التحصيلات غير المزامنة:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        subPayments.forEach { payment ->
                            HistoryItem(
                                title = "تحصيل دفعة بقيمة: ${payment.amount} ريال",
                                date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(payment.createdAt)),
                                status = payment.status,
                                isReading = false,
                                extra = if (payment.paymentMethod == "CASH") "نقدي" else "تحويل بنكي"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@Composable
fun HistoryItem(
    title: String,
    date: String,
    status: String,
    isReading: Boolean,
    extra: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isReading) Icons.Default.Speed else Icons.Default.Payment,
                    contentDescription = null,
                    tint = if (isReading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = date,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        extra?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = it,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Sync Badge status
            val badgeColor = when (status) {
                "synced" -> MaterialTheme.colorScheme.primary
                "pending" -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.error
            }
            val statusText = when (status) {
                "synced" -> "تمت المزامنة"
                "pending" -> "معلقة محلياً"
                else -> "فشل المزامنة"
            }

            Surface(
                color = badgeColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
