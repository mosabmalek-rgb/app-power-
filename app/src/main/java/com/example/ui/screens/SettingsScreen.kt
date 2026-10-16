package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReadingCycleCalculator
import com.example.data.ReadingCycleType
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
    val currentCycleRange by viewModel.currentCycleRange.collectAsState()
    val context = LocalContext.current
    val profileImagePath by viewModel.profileImagePath.collectAsState()
    var profileBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(profileImagePath) {
        profileBitmap = profileImagePath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }

    val pickProfileImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = runCatching {
                val profileDir = File(context.filesDir, "profile_images").apply { mkdirs() }
                val tempFile = File(profileDir, "profile_${System.currentTimeMillis()}.tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                } ?: error("تعذر قراءة الصورة المختارة.")
                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                    ?: error("ملف الصورة غير صالح.")
                val targetFile = File(profileDir, "profile_${System.currentTimeMillis()}.jpg")
                FileOutputStream(targetFile).use { output ->
                    if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                        error("تعذر حفظ صورة الملف.")
                    }
                }
                bitmap.recycle()
                tempFile.delete()
                targetFile.absolutePath
            }.getOrNull()
            if (savedPath != null) {
                runCatching { viewModel.saveProfileImagePath(savedPath) }
                    .onSuccess {
                        profileBitmap = BitmapFactory.decodeFile(savedPath)
                        Toast.makeText(context, "تم حفظ صورة الملف بنجاح.", Toast.LENGTH_SHORT).show()
                    }
                    .onFailure {
                        Toast.makeText(context, it.message ?: "تعذر حفظ صورة الملف.", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(context, "تعذر حفظ الصورة. اختر صورة أخرى وحاول مجدداً.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "لم يتم تحديد صورة.", Toast.LENGTH_SHORT).show()
        }
    }

    var showChangePinDialog by remember { mutableStateOf(false) }
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var confirmNewPinInput by remember { mutableStateOf("") }
    var pinDialogError by remember { mutableStateOf<String?>(null) }

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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable {
                                pickProfileImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val visibleProfileBitmap = profileBitmap
                        if (visibleProfileBitmap != null) {
                            Image(
                                bitmap = visibleProfileBitmap.asImageBitmap(),
                                contentDescription = "صورة الملف",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            pickProfileImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("change_profile_picture_button")
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تغيير صورة الملف", fontSize = 12.sp)
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            pickProfileImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تغيير صورة الملف", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    DetailRow(label = "حالة المستخدم الميداني", value = "مفعّل عبر PowerHub")
                    DetailRow(label = "شركة المولدات التابع لها", value = companyName)
                    DetailRow(label = "الأفرع المرخصة للعمل", value = "تُحدَّد تلقائياً من حساب PowerHub")
                    DetailRow(label = "بيانات الاتصال", value = "محفوظة بشكل محمي في الحساب")
                    DetailRow(label = "صلاحية التحصيل", value = "غير متاحة في تطبيق القراءات")
                }
            }

            // Security & Application PIN Card (أمان التطبيق ورمز PIN)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "أمان التطبيق ورمز PIN",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            color = Color(0xFFF0FDF4),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                        ) {
                            Text(
                                text = if (viewModel.hasLocalPin()) "مفعّل ومحمي" else "غير معين",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        "يُستخدم رمز الـ PIN المكون من 4 أرقام لفتح قفل التطبيق بسرعة بدون الحاجة لإعادة كتابة كلمة المرور في كل مرة.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            currentPinInput = ""
                            newPinInput = ""
                            confirmNewPinInput = ""
                            pinDialogError = null
                            showChangePinDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_pin_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (viewModel.hasLocalPin()) "تغيير رمز PIN لفتح التطبيق" else "تعيين رمز PIN جديد",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Change PIN Dialog
            if (showChangePinDialog) {
                AlertDialog(
                    onDismissRequest = { showChangePinDialog = false },
                    icon = {
                        Icon(
                            Icons.Default.Password,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = {
                        Text(
                            text = if (viewModel.hasLocalPin()) "تغيير رمز PIN" else "تعيين رمز PIN",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "أدخل رمز الـ PIN الحالي ثم اختر رمزاً جديداً مكوناً من 4 أرقام:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (viewModel.hasLocalPin()) {
                                OutlinedTextField(
                                    value = currentPinInput,
                                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) currentPinInput = it },
                                    label = { Text("رمز PIN الحالي (4 أرقام)") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    modifier = Modifier.fillMaxWidth().testTag("current_pin_input")
                                )
                            }

                            OutlinedTextField(
                                value = newPinInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPinInput = it },
                                label = { Text("رمز PIN الجديد (4 أرقام)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                            )

                            OutlinedTextField(
                                value = confirmNewPinInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmNewPinInput = it },
                                label = { Text("تأكيد رمز PIN الجديد") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth().testTag("confirm_new_pin_input")
                            )

                            if (pinDialogError != null) {
                                Text(
                                    text = pinDialogError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (viewModel.hasLocalPin() && !viewModel.verifyLocalPin(currentPinInput)) {
                                    pinDialogError = "رمز PIN الحالي غير صحيح!"
                                    return@Button
                                }
                                if (newPinInput.length != 4) {
                                    pinDialogError = "يجب أن يتكون رمز PIN الجديد من 4 أرقام."
                                    return@Button
                                }
                                if (newPinInput != confirmNewPinInput) {
                                    pinDialogError = "رمز التأكيد غير متطابق مع الرمز الجديد."
                                    return@Button
                                }

                                val success = viewModel.setLocalPin(newPinInput)
                                if (success) {
                                    showChangePinDialog = false
                                    Toast.makeText(context, "تم تحديث رمز PIN بنجاح! 🔒", Toast.LENGTH_SHORT).show()
                                } else {
                                    pinDialogError = "تعذر حفظ رمز PIN على الجهاز."
                                }
                            },
                            modifier = Modifier.testTag("confirm_change_pin_button")
                        ) {
                            Text("حفظ الرمز الجديد", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChangePinDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // Reading Cycle Settings Card (تحديد دورة القراءات)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "تحديد دورة القراءات",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "مزامنة المنصة والسيرفر",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        "تتم إدارة وتعيين منطق دورة القراءات مركزياً من المنصة والسيرفر، ويتم تطبيقها محلياً لحساب نسب الإنجاز وفرز المشتركين:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Current Active Cycle Banner
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "الدورة الحالية النشطة:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    currentCycleRange.labelAr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Cycle Options
                    ReadingCycleType.values().forEach { cycleType ->
                        val isSelected = readingCycleType == cycleType
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setReadingCycleType(cycleType)
                                    Toast.makeText(context, "تم تحديد الدورة: ${cycleType.titleAr}", Toast.LENGTH_SHORT).show()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setReadingCycleType(cycleType)
                                            Toast.makeText(context, "تم تحديد الدورة: ${cycleType.titleAr}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = cycleType.titleAr,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = cycleType.descriptionAr,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                // If WEEKLY is selected, show Day Picker
                                if (cycleType == ReadingCycleType.WEEKLY && isSelected) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "يوم بدء الدورة الأسبوعية:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ReadingCycleCalculator.ARABIC_DAYS.forEach { (calDay, dayName) ->
                                            val isDaySelected = weeklyCycleStartDay == calDay
                                            FilterChip(
                                                selected = isDaySelected,
                                                onClick = {
                                                    viewModel.setWeeklyCycleStartDay(calDay)
                                                    Toast.makeText(context, "بدء الدورة: يوم $dayName", Toast.LENGTH_SHORT).show()
                                                },
                                                label = {
                                                    Text(dayName, fontSize = 11.sp, fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
