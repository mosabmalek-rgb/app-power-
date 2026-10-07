package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import com.example.R
import com.example.data.MeterReadingOcr
import com.example.ui.FieldReadingViewModel
import com.google.android.gms.location.LocationServices
import java.io.File
import kotlin.math.*

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
    var isReadingReset by remember { mutableStateOf(false) }

    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    var extractedReading by remember { mutableStateOf<String?>(null) }
    var ocrMessage by remember { mutableStateOf<String?>(null) }
    var readingApproved by remember { mutableStateOf(false) }

    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var gpsReady by remember { mutableStateOf(false) }
    var lastConfirmedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var pendingLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }

    val fusedLocation = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun updateLocation(showAlertOnDistanceMismatch: Boolean = true) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocation.lastLocation.addOnSuccessListener { location ->
            if (location == null) {
                latitude = null
                longitude = null
                gpsReady = false
                return@addOnSuccessListener
            }

            val newLocation = location.latitude to location.longitude

            if (showAlertOnDistanceMismatch && lastConfirmedLocation != null) {
                val distance = haversineMeters(
                    lastConfirmedLocation!!.first,
                    lastConfirmedLocation!!.second,
                    location.latitude,
                    location.longitude
                )
                if (distance > 5.0) {
                    pendingLocation = newLocation
                    showLocationDialog = true
                    return@addOnSuccessListener
                }
            }

            latitude = location.latitude
            longitude = location.longitude
            gpsReady = true
            lastConfirmedLocation = newLocation
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) updateLocation(showAlertOnDistanceMismatch = true)
        else Toast.makeText(context, "تم حفظ القراءة بدون موقع لأن الإذن لم يُمنح.", Toast.LENGTH_SHORT).show()
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        capturedPhotoPath = if (success) pendingPhotoPath else null
        extractedReading = null
        ocrMessage = null
        readingApproved = false

        if (success && pendingPhotoUri != null) {
            MeterReadingOcr.extract(context, pendingPhotoUri!!) { value, message ->
                extractedReading = value
                ocrMessage = message
                if (value != null) currentReadingStr = value
            }
        }

        Toast.makeText(context, if (success) "تم التقاط صورة القراءة." else "لم يتم حفظ الصورة.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            updateLocation(showAlertOnDistanceMismatch = false)
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    if (showLocationDialog && pendingLocation != null) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("تنبيه موقع", fontWeight = FontWeight.Bold) },
            text = {
                val dist = if (lastConfirmedLocation != null) haversineMeters(lastConfirmedLocation!!.first, lastConfirmedLocation!!.second, pendingLocation!!.first, pendingLocation!!.second) else 0.0
                Text("اختلفت إحداثيات الموقع الحالية عن الموقع الموثق سابقاً بمقدار ${String.format("%.2f", dist)} متر.\nهل تريد اعتماد الإحداثيات الجديدة؟")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        latitude = pendingLocation!!.first
                        longitude = pendingLocation!!.second
                        gpsReady = true
                        lastConfirmedLocation = pendingLocation
                        pendingLocation = null
                        showLocationDialog = false
                    }
                ) {
                    Text("اعتماد الجديد", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingLocation = null
                        showLocationDialog = false
                    }
                ) {
                    Text("الاحتفاظ بالموقع الحالي")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إدخال قراءة المشترك", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { innerPadding ->
        if (subscriber == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val sub = subscriber!!
            val lastReading = sub.lastReading
            val currentReadingVal = currentReadingStr.toDoubleOrNull() ?: 0.0
            val consumption = when {
                currentReadingStr.isEmpty() -> 0.0
                isReadingReset -> currentReadingVal
                else -> currentReadingVal - lastReading
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(sub.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("الرقم المرجعي: ${sub.refNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("الفرع: ${sub.branchId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isReadingReset, onCheckedChange = { isReadingReset = it }, modifier = Modifier.testTag("reading_reset_checkbox"))
                            Column {
                                Text("إعادة ضبط سجل قراءة المشترك", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("تجاوز شرط أن القراءة الحالية أكبر من السابقة", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        OutlinedTextField(value = "${sub.lastReading} ك.و/س", onValueChange = {}, label = { Text("آخر قراءة مسجلة (سابقة)") }, readOnly = true, leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                        OutlinedTextField(
                            value = currentReadingStr,
                            onValueChange = { input -> if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) currentReadingStr = input },
                            label = { Text("القراءة الحالية للمشترك") },
                            placeholder = { Text("أدخل القيمة أو التقط صورة بالكاميرا") },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val imageDir = File(context.filesDir, "meter_images").apply { mkdirs() }
                                        val photoFile = File(imageDir, "reading_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                        pendingPhotoUri = uri
                                        pendingPhotoPath = photoFile.absolutePath
                                        takePicture.launch(uri)
                                    },
                                    modifier = Modifier.testTag("camera_inside_reading_input")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "التقاط صورة للعداد واستخراج الرقم",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("current_reading_input"),
                            shape = RoundedCornerShape(10.dp), singleLine = true
                        )
                        if (currentReadingStr.isNotEmpty()) {
                            val isInvalid = !isReadingReset && currentReadingVal < lastReading
                            Card(colors = CardDefaults.cardColors(containerColor = if (isInvalid) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (isInvalid) Text("خطأ: القراءة الحالية يجب أن تكون أكبر من أو تساوي القراءة السابقة ($lastReading) إلا في حال تفعيل إعادة ضبط سجل القراءة أعلاه.", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    else Text(if (isReadingReset) "نمط إعادة الضبط: احتساب القراءة الحالية كبداية جديدة" else "الاستهلاك الحالي المحتسب تلقائياً: ${consumption} كيلو واط /ساعة", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("التحقق والتوثيق الميداني", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("صورة القراءة الميدانية", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("التقاط صورة لإرفاقها كإثبات للقراءة", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Button(onClick = {
                                val imageDir = File(context.filesDir, "meter_images").apply { mkdirs() }
                                val photoFile = File(imageDir, "reading_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
                                pendingPhotoUri = uri
                                pendingPhotoPath = photoFile.absolutePath
                                takePicture.launch(uri)
                            }, modifier = Modifier.testTag("capture_photo_button")) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("التقاط")
                            }
                        }

                        AnimatedVisibility(visible = capturedPhotoPath != null) {
                            val bitmap = remember(capturedPhotoPath) {
                                capturedPhotoPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "صورة القراءة الملتقطة",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                )
                            } else {
                                Text("تم التقاط الصورة ولكن تعذر عرض المعاينة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        if (capturedPhotoPath != null && (extractedReading != null || ocrMessage != null)) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("الرقم المستخلص من الصورة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (extractedReading != null) {
                                        OutlinedTextField(value = extractedReading!!, onValueChange = { extractedReading = it; readingApproved = false }, label = { Text("القراءة المستخلصة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("ocr_reading_input"), singleLine = true)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { readingApproved = false; currentReadingStr = extractedReading.orEmpty() }, modifier = Modifier.weight(1f).testTag("change_ocr_reading_button")) { Text("تغيير القراءة") }
                                            Button(onClick = { currentReadingStr = extractedReading.orEmpty(); readingApproved = true }, modifier = Modifier.weight(1f).testTag("approve_ocr_reading_button")) { Text("اعتماد القراءة") }
                                        }
                                        Text(if (readingApproved) "تم اعتماد الرقم المستخلص، ويمكن حفظ القراءة." else "راجع الرقم ثم اضغط اعتماد القراءة قبل الحفظ.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    } else {
                                        Text(ocrMessage ?: "لم يتم استخراج رقم واضح.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        OutlinedButton(onClick = { currentReadingStr = ""; ocrMessage = null }, modifier = Modifier.testTag("change_ocr_reading_button")) { Text("تغيير القراءة وإدخالها يدوياً") }
                                    }
                                }
                            }
                        }

                        HorizontalDivider()
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = if (gpsReady) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed, contentDescription = null, tint = if (gpsReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("إحداثيات الموقع الميداني تلقائياً (GPS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (gpsReady && latitude != null && longitude != null) Text("خط عرض: ${String.format("%.5f", latitude)} | خط طول: ${String.format("%.5f", longitude)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                else Text("جاري استخلاص موقع القراءة الجغرافي...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                        // تم حذف سطر التحقق الميداني غير الضروري
                    }
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات ميدانية إضافية") }, placeholder = { Text("أدخل أي ملاحظات حول القراءة أو موقع العميل...") }, leadingIcon = { Icon(Icons.Default.Comment, contentDescription = null) }, modifier = Modifier.fillMaxWidth().testTag("notes_input_field"), shape = RoundedCornerShape(10.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val readingVal = currentReadingStr.toDoubleOrNull()
                        if (readingVal == null) {
                            Toast.makeText(context, "الرجاء إدخال رقم قراءة صحيح أولاً", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (capturedPhotoPath != null && !readingApproved) {
                            Toast.makeText(context, "راجع القراءة المستخلصة واضغط اعتماد القراءة أولاً.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveReading(subscriberId = sub.id, currentReading = readingVal, notes = notes, latitude = latitude ?: 0.0, longitude = longitude ?: 0.0, photoPath = capturedPhotoPath, isReadingReset = isReadingReset, fieldVerified = true) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (success) onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_reading_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
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
