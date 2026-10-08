package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FieldReadingViewModel

@Composable
fun PinScreen(
    viewModel: FieldReadingViewModel,
    setupMode: Boolean,
    onSuccess: () -> Unit,
    onLogout: () -> Unit = {},
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // Offline capability badge
        Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SignalWifiOff,
                    contentDescription = null,
                    tint = Color(0xFF1D4ED8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "دخول محلي آمن بدون إنترنت (Offline Ready)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF)
                )
            }
        }

        Text(
            text = if (setupMode) "أنشئ رمز فتح التطبيق (PIN)" else "أدخل رمز فتح التطبيق (PIN)",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (setupMode) 
                "اختر رمز PIN محلياً من 4 أرقام. سيُستخدم لفتح التطبيق والعمل في الميدان أوفلاين دون إنترنت بعد تسجيل الدخول الأول." 
            else 
                "يمكنك فتح التطبيق ومتابعة العمل الميداني بالكامل بدون اتصال بالإنترنت ما دامت الجلسة مسجلة على الجهاز.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("رمز PIN (4 أرقام)") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(if (setupMode) "pin_setup_input" else "pin_unlock_input"),
                )
                if (setupMode) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { confirmation = it; error = null } },
                        label = { Text("تأكيد رمز PIN") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pin_confirmation_input"),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = {
                        when {
                            pin.length != 4 -> error = "يجب أن يتكون PIN من 4 أرقام."
                            setupMode && confirmation != pin -> error = "رمزا PIN غير متطابقين."
                            setupMode && !viewModel.setLocalPin(pin) -> error = "تعذر حفظ PIN على الجهاز."
                            !setupMode && !viewModel.verifyLocalPin(pin) -> error = "رمز PIN غير صحيح."
                            else -> onSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(if (setupMode) "save_pin_button" else "unlock_pin_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (setupMode) "حفظ PIN والمتابعة للميدان" else "فتح التطبيق ومتابعة العمل",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!setupMode) {
                    OutlinedButton(
                        onClick = onLogout, 
                        modifier = Modifier.fillMaxWidth().testTag("pin_logout_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تسجيل الخروج واستخدام كلمة المرور", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
