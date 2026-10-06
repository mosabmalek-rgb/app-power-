package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.FieldReadingViewModel

@Composable
fun ChangePasswordScreen(viewModel: FieldReadingViewModel, onSuccess: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("تغيير كلمة المرور", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text("هذه كلمة مرور مؤقتة. يجب إنشاء كلمة مرور جديدة قبل استخدام التطبيق.", modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("كلمة المرور الجديدة") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it; error = null },
            label = { Text("تأكيد كلمة المرور") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Button(
            onClick = {
                when {
                    password.length < 8 -> error = "يجب أن تتكون كلمة المرور من 8 رموز على الأقل."
                    password != confirmation -> error = "كلمتا المرور غير متطابقتين."
                    else -> {
                        saving = true
                        viewModel.changeTemporaryPassword(password) { success, message ->
                            saving = false
                            if (success) onSuccess() else error = message
                        }
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) { Text(if (saving) "جارٍ الحفظ…" else "حفظ والمتابعة") }
    }
}
