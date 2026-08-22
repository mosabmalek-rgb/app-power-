package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.FieldReadingViewModel
import com.google.android.gms.location.LocationServices
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReadingScreen(
    viewModel: FieldReadingViewModel,
    onBack: () -> Unit
) {
    val subscriber by viewModel.selectedSubscriber.collectAsState()
    val context = LocalContext.current

    var currentReadingStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isMeterReplacement by remember { mutableStateOf(false) }
    var fieldVerified by remember { mutableStateOf(true) }

    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var gpsReady by remember { mutableStateOf(false) }
    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }
    fun updateLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        fusedLocation.lastLocation.addOnSuccessListener { location ->
            latitude = location?.latitude
            longitude = location?.longitude
            gpsReady = location != null
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) updateLocation() else Toast.makeText(context, "تم حفظ القراءة بدون موقع لأن الإذن لم يُمنح.", Toast.LENGTH_SHORT).show()
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        capturedPhotoPath = if (success) pendingPhotoPath else null
        Toast.makeText(context, if (success) "تم التقاط صورة العداد." else "لم يتم حفظ الصورة.", Toast.LENGTH_SHORT).show()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) updateLocation()
        else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدخال قراءة العداد", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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
        if (subscriber == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val sub = subscriber!!
            
            // Calculate consumption automatically
            val lastReading = sub.lastReading
            val currentReadingVal = currentReadingStr.toDoubleOrNull() ?: 0.0
            
            val consumption = if (currentReadingStr.isEmpty()) {
                0.0
            } else if (isMeterReplacement) {
                currentReadingVal // Reset consumption or simply report current reading
            } else {
                currentReadingVal - lastReading
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Subscriber header card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = sub.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "رقم العداد: ${sub.refNumber}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "الفرع: ${sub.branchId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Readings Inputs
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        
                        // Option for Meter Replacement
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isMeterReplacement,
                                onCheckedChange = { isMeterReplacement = it },
                                modifier = Modifier.testTag("meter_replacement_checkbox")
                            )
                            Column {
                                Text("تم استبدال العداد في الموقع", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("تجاوز شرط أن القراءة الحالية أكبر من السابقة", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        // Last reading (read-only)
                        OutlinedTextField(
                            value = "${sub.lastReading} ك.و/س",
                            onValueChange = {},
                            label = { Text("آخر قراءة مسجلة (سابقة)") },
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Current reading input
                        OutlinedTextField(
                            value = currentReadingStr,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                                    currentReadingStr = input
                                }
                            },
                            label = { Text("القراءة الحالية لعداد الكهرباء") },
                            placeholder = { Text("أدخل الأرقام الظاهرة على شاشة العداد") },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("current_reading_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        // Auto-computed Consumption calculation banner
                        if (currentReadingStr.isNotEmpty()) {
                            val isInvalid = !isMeterReplacement && currentReadingVal < lastReading
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isInvalid) MaterialTheme.colorScheme.errorContainer 
                                                     else MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (isInvalid) {
                                        Text(
                                            text = "خطأ: القراءة الحالية يجب أن تكون أكبر من أو تساوي السابقة ($lastReading) إلا في حال تفعيل خيار استبدال العداد أعلاه.",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Text(
                                            text = if (isMeterReplacement) "نمط استبدال العداد: تصفير واحتساب قراءة البداية"
                                                   else "الاستهلاك الحالي المحتسب تلقائياً: ${consumption} كيلو واط /ساعة",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Camera & GPS Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("التحقق والتوثيق الميداني", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                        // Camera section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("صورة العداد الميداني", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("التقاط صورة للعداد لإرفاقها كإثبات للقراءة", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            
                            Button(
                                onClick = {
                                    val photoFile = File(context.cacheDir, "meter_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                    pendingPhotoUri = uri
                                    pendingPhotoPath = photoFile.absolutePath
                                    takePicture.launch(uri)
                                },
                                modifier = Modifier.testTag("capture_photo_button")
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("التقاط")
                            }
                        }

                        // Simulated Image preview
                        AnimatedVisibility(visible = capturedPhotoPath != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // Show our generated logo as placeholder meter visual
                                Image(
                                    painter = painterResource(id = R.drawable.powerhub_logo_foreground_1787388603387),
                                    contentDescription = "صورة العداد الملتقطة",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Text(
                                        text = "معاينة صورة العداد المقترنة بالقراءة",
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }

                        Divider()

                        // GPS Status Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (gpsReady) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                                contentDescription = null,
                                tint = if (gpsReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("إحداثيات الموقع الميداني تلقائياً (GPS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (gpsReady) {
                                    Text(
                                        text = if (latitude != null && longitude != null) "خط عرض: ${String.format("%.5f", latitude)} | خط طول: ${String.format("%.5f", longitude)}" else "لم يتم تحديد الموقع",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text("جاري استخلاص موقع العداد الجغرافي...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }

                        Divider()

                        // Verified checklist option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = fieldVerified,
                                onCheckedChange = { fieldVerified = it },
                                modifier = Modifier.testTag("field_verified_checkbox")
                            )
                            Column {
                                Text("تم التحقق ميدانياً وبصرياً من سلامة العداد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("يؤكد الفني خلو العداد من التلاعب أو الأعطال", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // Notes input field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات ميدانية إضافية") },
                    placeholder = { Text("أدخل أي ملاحظات حول العداد أو موقع العميل...") },
                    leadingIcon = { Icon(Icons.Default.Comment, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("notes_input_field"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action Save Button
                Button(
                    onClick = {
                        val readingVal = currentReadingStr.toDoubleOrNull()
                        if (readingVal == null) {
                            Toast.makeText(context, "الرجاء إدخال رقم قراءة صحيح أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        viewModel.saveReading(
                            subscriberId = sub.id,
                            currentReading = readingVal,
                            notes = notes,
                            latitude = latitude ?: 0.0,
                            longitude = longitude ?: 0.0,
                            photoPath = capturedPhotoPath,
                            isMeterReplacement = isMeterReplacement
                        ) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (success) {
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_reading_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ القراءة محلياً", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
