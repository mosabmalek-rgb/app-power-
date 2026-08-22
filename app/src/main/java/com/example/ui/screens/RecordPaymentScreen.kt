package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FieldReadingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentScreen(
    viewModel: FieldReadingViewModel,
    onBack: () -> Unit
) {
    val subscriber by viewModel.selectedSubscriber.collectAsState()
    val context = LocalContext.current

    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("CASH") } // "CASH" or "BANK"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تسجيل دفعة مالية للعميل", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) },
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "صاحب الاشتراك: ${sub.name}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الرقم المرجعي: ${sub.refNumber}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "الرصيد المستحق الحالي: ${sub.balance} ريال",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sub.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Payment Inputs Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("بيانات التحصيل النقدي والبنكي", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

                        // Payment Method Selection
                        Column {
                            Text("طريقة الدفع أو التحصيل:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Cash
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = paymentMethod == "CASH",
                                        onClick = { paymentMethod = "CASH" },
                                        modifier = Modifier.testTag("method_cash")
                                    )
                                    Text("نقدي (كاش)")
                                }

                                // Bank
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = paymentMethod == "BANK",
                                        onClick = { paymentMethod = "BANK" },
                                        modifier = Modifier.testTag("method_bank")
                                    )
                                    Text("تحويل بنكي / شبكة")
                                }
                            }
                        }

                        // Amount field
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                                    amountStr = input
                                }
                            },
                            label = { Text("المبلغ المحصل (ريال سعودي)") },
                            placeholder = { Text("أدخل قيمة المبلغ المسدد نقداً أو عبر البنك") },
                            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("payment_amount_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                }

                // Notes/receipt field
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات الدفعة / رقم التحويل أو السند") },
                    placeholder = { Text("مثال: تم الاستلام نقداً وسند رقم 10403 أو رقم التحويل البنكي") },
                    leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("payment_notes_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Submit Action Button
                Button(
                    onClick = {
                        val amountVal = amountStr.toDoubleOrNull()
                        if (amountVal == null || amountVal <= 0) {
                            Toast.makeText(context, "الرجاء إدخال مبلغ دفع صالح وموجب", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        viewModel.savePayment(
                            subscriberId = sub.id,
                            amount = amountVal,
                            paymentMethod = paymentMethod,
                            notes = notes
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
                        .testTag("save_payment_submit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ الدفعة محلياً", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
