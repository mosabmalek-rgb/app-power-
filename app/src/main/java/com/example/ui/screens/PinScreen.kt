package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (setupMode) "أنشئ رمز فتح التطبيق" else "أدخل رمز فتح التطبيق",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (setupMode) "اختر PIN محلياً من 4 أرقام. سيُستخدم لفتح التطبيق بعد تسجيل الدخول الأول." else "يمكنك فتح التطبيق دون إعادة كتابة كلمة المرور ما دامت جلسة الجهاز صالحة.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("PIN من 4 أرقام") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(if (setupMode) "pin_setup_input" else "pin_unlock_input"),
                )
                if (setupMode) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { confirmation = it; error = null } },
                        label = { Text("تأكيد PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("pin_confirmation_input"),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Button(
                    onClick = {
                        when {
                            pin.length != 4 -> error = "يجب أن يتكون PIN من 4 أرقام."
                            setupMode && confirmation != pin -> error = "رمزا PIN غير متطابقين."
                            setupMode && !viewModel.setLocalPin(pin) -> error = "تعذر حفظ PIN على الجهاز."
                            !setupMode && !viewModel.verifyLocalPin(pin) -> error = "PIN غير صحيح."
                            else -> onSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(if (setupMode) "save_pin_button" else "unlock_pin_button"),
                ) {
                    Text(if (setupMode) "حفظ PIN والمتابعة" else "فتح التطبيق")
                }
                if (!setupMode) {
                    Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().testTag("pin_logout_button")) {
                        Text("تسجيل الخروج واستخدام كلمة المرور")
                    }
                }
            }
        }
    }
}
