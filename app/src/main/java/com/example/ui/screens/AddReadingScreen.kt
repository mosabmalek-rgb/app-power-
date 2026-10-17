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
import androidx.compose.ui.graphics.Color
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
    var isOcrProcessing by remember { mutableStateOf(false) }

    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var gpsReady by remember { mutableStateOf(false) }
    var lastConfirmedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var pendingLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showSanityConfirmDialog by remember { mutableStateOf(false) }
    var sanityWarningMessage by remember { mutableStateOf("") }

    // When subscriber changes (e.g. Next subscriber clicked), reset fields
    LaunchedEffect(subscriber?.id) {
        currentReadingStr = ""
        notes = ""
        isReadingReset = false
        capturedPhotoPath = null
        extractedReading = null
        ocrMessage = null
        readingApproved = false
        isOcrProcessing = false
    }

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
        isOcrProcessing = success && pendingPhotoUri != null

        if (success && pendingPhotoUri != null) {
            MeterReadingOcr.extract(context, pendingPhotoUri!!) { value, message ->
                isOcrProcessing = false
                extractedReading = value
                ocrMessage = message
                if (value != null) {
                    currentReadingStr = value
                    readingApproved = false
                    Toast.makeText(context, "تم استخراج القراءة $value. راجع الرقم واضغط «اعتماد القراءة» قبل الحفظ.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, message ?: "تعذر استخراج الرقم؛ يمكنك إدخاله يدوياً.", Toast.LENGTH_LONG).show()
                }
            }
        }

        Toast.makeText(context, if (success) "تم التقاط الصورة، جارٍ استخراج رقم العداد..." else "لم يتم حفظ الصورة.", Toast.LENGTH_SHORT).show()
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

    // Sanity check alert dialog for abnormal or negative consumption
    if (showSanityConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSanityConfirmDialog = false },
            icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("تأكيد اعتماد القراءة", fontWeight = FontWeight.Bold) },
            text = { Text(sanityWarningMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        showSanityConfirmDialog = false
                        val readingVal = currentReadingStr.toDoubleOrNull() ?: return@Button
                        val sub = subscriber ?: return@Button
                        viewModel.saveReading(
                            subscriberId = sub.id,
                            currentReading = readingVal,
                            notes = notes,
                            latitude = latitude ?: 0.0,
                            longitude = longitude ?: 0.0,
                            photoPath = capturedPhotoPath,
                            isReadingReset = isReadingReset,
                            fieldVerified = true,
                            readingValueText = currentReadingStr
                        ) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (success) {
                                val nextSub = viewModel.getNextUnreadSubscriber(sub.id)
                                if (nextSub != null) {
                                    viewModel.selectSubscriber(nextSub.id)
                                    Toast.makeText(context, "تم الانتقال للمشترك التالي: ${nextSub.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    onBack()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، اعتمد القراءة واحفظ")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSanityConfirmDialog = false }) {
                    Text("مراجعة الرقم وتعديله")
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
                            onValueChange = { input -> if (input.isEmpty() || input.matches(Regex("\\d*(?:\\.\\d{0,2})?"))) currentReadingStr = input },
                            label = { Text("القراءة الحالية للمشترك") },
                            placeholder = { Text("مثال: 128 أو 128.5 أو 128.52") },
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("current_reading_input"),
                            shape = RoundedCornerShape(10.dp), singleLine = true
                        )
                        if (isOcrProcessing) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("جارٍ استخراج رقم العداد من الصورة تلقائياً...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (extractedReading != null) {
                            Text("تم استخراج القراءة: $extractedReading — راجعها في الحقل أعلاه ويمكنك تعديلها.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        } else if (ocrMessage != null && capturedPhotoPath != null) {
                            Text(ocrMessage!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }

                        if (currentReadingStr.isNotEmpty()) {
                            val isInvalid = !isReadingReset && currentReadingVal < lastReading
                            val isVeryHigh = !isReadingReset && consumption > 500.0 && consumption > lastReading * 3
                            
                            val cardBg = when {
                                isInvalid -> MaterialTheme.colorScheme.errorContainer
                                isVeryHigh -> Color(0xFFFFF3CD)
                                else -> Color(0xFFF0FDF4)
                            }
                            val cardBorder = when {
                                isInvalid -> MaterialTheme.colorScheme.error
                                isVeryHigh -> Color(0xFFFFC107)
                                else -> Color(0xFF86EFAC)
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isInvalid) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "خطأ: القراءة الحالية أقل من السابقة ($lastReading ك.و)",
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            "يجب تفعيل خيار «إعادة ضبط سجل القراءة» أعلاه إذا تم استبدال أو تصفير العداد.",
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                                            fontSize = 11.sp
                                        )
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    if (isVeryHigh) Icons.Default.WarningAmber else Icons.Default.ElectricBolt,
                                                    contentDescription = null,
                                                    tint = if (isVeryHigh) Color(0xFF856404) else Color(0xFF166534),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (isReadingReset) "نمط إعادة الضبط (بداية جديدة)" else "الاستهلاك المحتسب تلقائياً:",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isVeryHigh) Color(0xFF856404) else Color(0xFF166534)
                                                )
                                            }
                                            Text(
                                                text = "${String.format(java.util.Locale.US, "%.1f", consumption)} ك.و/س",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 15.sp,
                                                color = if (isVeryHigh) Color(0xFF856404) else Color(0xFF166534)
                                            )
                                        }
                                        if (isVeryHigh) {
                                            Text(
                                                "⚠️ تنبيه: الاستهلاك مرتفع جداً مقارنة بالمعدل السابق، يرجى التأكد من العداد.",
                                                fontSize = 11.sp,
                                                color = Color(0xFF856404),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
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
                                        OutlinedTextField(value = extractedReading!!, onValueChange = { input -> if (input.isEmpty() || input.matches(Regex("\\d*(?:\\.\\d{0,2})?"))) { extractedReading = input; readingApproved = false } }, label = { Text("القراءة المستخلصة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("ocr_reading_input"), singleLine = true)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { currentReadingStr = extractedReading.orEmpty(); extractedReading = null; ocrMessage = null; readingApproved = false }, modifier = Modifier.weight(1f).testTag("change_ocr_reading_button")) { Text("تغيير القراءة") }
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
                Spacer(modifier = Modifier.height(14.dp))

                val executeSave = { navigateToNext: Boolean ->
                    val readingVal = currentReadingStr.toDoubleOrNull()
                    if (readingVal == null) {
                        Toast.makeText(context, "الرجاء إدخال رقم قراءة صحيح أولاً", Toast.LENGTH_SHORT).show()
                    } else if (!currentReadingStr.matches(Regex("^\\d+(?:\\.\\d{1,2})?$"))) {
                        Toast.makeText(context, "أدخل قراءة صحيحة بمنزلة عشرية واحدة أو منزلتين كحد أقصى.", Toast.LENGTH_SHORT).show()
                    } else if (isOcrProcessing) {
                        Toast.makeText(context, "انتظر حتى ينتهي استخراج رقم العداد.", Toast.LENGTH_SHORT).show()
                    } else if (extractedReading != null && !readingApproved) {
                        Toast.makeText(context, "راجع الرقم المستخلص واضغط «اعتماد القراءة» أو أدخل القراءة يدوياً قبل الحفظ.", Toast.LENGTH_LONG).show()
                    } else if (!isReadingReset && readingVal < sub.lastReading) {
                        Toast.makeText(context, "القراءة أقل من السابقة! قم بتفعيل إعادة الضبط إذا تم تصفير العداد.", Toast.LENGTH_LONG).show()
                    } else if (!isReadingReset && consumption > 500.0 && consumption > sub.lastReading * 3) {
                        sanityWarningMessage = "الاستهلاك المحتسب (${consumption} ك.و) يبدو مرتفعاً بشكل غير معتاد مقارنة بالقراءة السابقة (${sub.lastReading} ك.و).\nهل أنت متأكد من صحة الرقم؟"
                        showSanityConfirmDialog = true
                    } else {
                        viewModel.saveReading(
                            subscriberId = sub.id,
                            currentReading = readingVal,
                            notes = notes,
                            latitude = latitude ?: 0.0,
                            longitude = longitude ?: 0.0,
                            photoPath = capturedPhotoPath,
                            isReadingReset = isReadingReset,
                            fieldVerified = true,
                            readingValueText = currentReadingStr
                        ) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (success) {
                                if (navigateToNext) {
                                    val nextSub = viewModel.getNextUnreadSubscriber(sub.id)
                                    if (nextSub != null) {
                                        viewModel.selectSubscriber(nextSub.id)
                                        Toast.makeText(context, "الانتقال للمشترك التالي: ${nextSub.name}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "تم إنجاز جميع المشتركين المتبقين! 🎉", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                } else {
                                    onBack()
                                }
                            }
                        }
                    }
                }

                // Primary Quick Save & Next Button
                Button(
                    onClick = { executeSave(true) },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_and_next_reading_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ والانتقال للمشترك التالي مباشرة ⚡", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Save & Exit Button
                OutlinedButton(
                    onClick = { executeSave(false) },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_reading_submit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ والعودة للقائمة", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
