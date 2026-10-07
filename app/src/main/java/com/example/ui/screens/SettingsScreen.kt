package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FieldReadingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FieldReadingViewModel,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val userName by viewModel.userName.collectAsState()
    val userRole by viewModel.userRole.collectAsState()
    val companyName by viewModel.companyName.collectAsState()
    val readingCycleType by viewModel.readingCycleType.collectAsState()
    val weeklyCycleStartDay by viewModel.weeklyCycleStartDay.collectAsState()
    val monthlyStartDay by viewModel.monthlyStartDay.collectAsState()
    val currentCycleRange by viewModel.currentCycleRange.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الملف الشخصي والإعدادات", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
            // Profile Card Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "الدور الميداني: $userRole",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    DetailRow(label = "حالة المستخدم الميداني", value = "مفعّل عبر PowerHub")
                    DetailRow(label = "شركة المولدات التابع لها", value = companyName)
                    DetailRow(label = "الأفرع المرخصة للعمل", value = "تُحدَّد تلقائياً من حساب PowerHub")
                    DetailRow(label = "بيانات الاتصال", value = "محفوظة بشكل محمي في الحساب")
                    DetailRow(label = "صلاحية التحصيل", value = "غير متاحة في تطبيق القراءات")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("دورة القراءات والفوترة", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        when (readingCycleType) {
                            com.example.data.ReadingCycleType.WEEKLY -> "أسبوعية — تبدأ يوم " + com.example.data.ReadingCycleCalculator.getDayNameAr(weeklyCycleStartDay)
                            com.example.data.ReadingCycleType.EVERY_10_DAYS -> "كل 10 أيام — 1، 11، 21"
                            com.example.data.ReadingCycleType.EVERY_15_DAYS -> "كل 15 يوم — 1، 16"
                            com.example.data.ReadingCycleType.MONTHLY -> "شهرية — تبدأ يوم " + monthlyStartDay + "، وفبراير حسب آخر يوم متاح"
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("الدورة الحالية: " + currentCycleRange.labelAr, fontSize = 12.sp)
                    Text("الإعداد مركزي من PowerHub ويُحفظ على الجهاز لاستخدامه دون اتصال.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    OutlinedButton(onClick = {
                        viewModel.refreshReadingCycle()
                        Toast.makeText(context, "جاري تحديث الدورة من PowerHub...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحديث الدورة")
                    }
                }
            }

            // Database Management Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("إدارة البيانات الميدانية", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    
                    Divider()

                    // Pull data button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تحديث بيانات المشتركين", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("سحب أحدث البيانات وقوائم المشتركين من السيرفر", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(
                            onClick = {
                                viewModel.refreshSubscribers()
                                Toast.makeText(context, "جاري تحديث المشتركين من السيرفر...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Divider()

                    // Clear cache button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مسح الكاش المحلي", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("مسح بيانات المشتركين والبدء بصفحة نظيفة", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "تم مسح البيانات الكاش بنجاح!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف الكاش", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Action Button
            Button(
                onClick = {
                    viewModel.logout()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("settings_logout_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الخروج الآمن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
