package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FieldReadingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    viewModel: FieldReadingViewModel,
    onBack: () -> Unit
) {
    val pendingReadings by viewModel.pendingReadings.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val pendingReadingsCount = pendingReadings.count { it.status == "pending" || it.status == "syncing" }
    val retryableReadings = pendingReadings.filter { it.status == "retryable" }
    val needsReviewReadings = pendingReadings.filter { it.status == "needs_review" }
    val totalPending = pendingReadingsCount + retryableReadings.size + needsReviewReadings.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المزامنة السحابية الميدانية", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Queue statistics card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "ملخص طابور الانتظار المحلي",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Divider()

                    SyncQueueRow(label = "عدد قراءات العدادات المعلقة", value = pendingReadingsCount.toString(), color = MaterialTheme.colorScheme.primary)
                    SyncQueueRow(label = "إعادة محاولة تلقائية", value = retryableReadings.size.toString(), color = MaterialTheme.colorScheme.tertiary)
                    SyncQueueRow(label = "تحتاج مراجعة الفني", value = needsReviewReadings.size.toString(), color = MaterialTheme.colorScheme.error)
                    SyncQueueRow(label = "عدد الصور والملفات المرفقة", value = pendingReadings.count { it.photoPath != null && it.status != "synced" }.toString(), color = MaterialTheme.colorScheme.secondary)
                    
                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إجمالي العمليات المعلقة بالتطبيق", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "$totalPending عملية",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (totalPending > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Sync actions block
            if (totalPending > 0) {
                Button(
                    onClick = { viewModel.syncAllData() },
                    enabled = !isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sync_start_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("جاري ترحيل الميدان...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مزامنة وإرسال للمنصة الآن", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("كل البيانات متزامنة بالكامل", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            Text("لقد قمت بمزامنة وترحيل كافة العمليات بنجاح.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            if (needsReviewReadings.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("قراءات تحتاج مراجعة قبل الإرسال", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        needsReviewReadings.forEach { reading ->
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("المشترك #${reading.subscriberId} — قراءة ${reading.currentReading}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(reading.lastError ?: "تعذر تحديد سبب الفشل.", fontSize = 11.sp)
                                Text("محاولات الإرسال: ${reading.retryCount}", fontSize = 11.sp)
                                OutlinedButton(
                                    onClick = { viewModel.requeueReadingForReview(reading.localId) },
                                    enabled = !isSyncing,
                                    modifier = Modifier.testTag("requeue_reading_${reading.localId}")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إعادة إدراج في الطابور")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Progress Indicators
            AnimatedVisibility(visible = isSyncing || syncStatusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("مراقبة التقدم الفني للترحيل", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = "$syncProgress%", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }

                        LinearProgressIndicator(
                            progress = syncProgress / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = syncStatusMessage,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }

            // Sync benefits / information block
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("قواعد المزامنة السحابية:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    BulletPoint("يمكنك الاستمرار بالعمل وتسجيل القراءات في الميدان دون توفر اتصال بالإنترنت.")
                    BulletPoint("يتم تخزين القراءات مع صورها والموقع الجغرافي بالكامل في قاعدة البيانات الآمنة المدمجة بالتطبيق.")
                    BulletPoint("فور توفر الإنترنت، يحاول التطبيق المزامنة في الخلفية مع تدرج آمن لإعادة المحاولة، ويمكنك تشغيلها يدوياً في أي وقت.")
                    BulletPoint("تظل أخطاء الصلاحية أو البيانات محفوظة للمراجعة ولا تُحذف القراءة من الجهاز حتى تتأكد مزامنتها أو تعيد إدراجها بنفسك.")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SyncQueueRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
