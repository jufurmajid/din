package com.blusher.cosmetics

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import android.provider.MediaStore
import android.os.Environment
import android.widget.Toast
import android.net.Uri
import android.content.ContentUris
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class Payment(val amount: Double, val date: String)
data class DebtAddition(val amount: Double, val date: String)
data class DebtTransaction(val type: String, val amount: Double, val date: String)
data class Debt(val id: Long, val person: String, val amount: Double, val debtDate: String, val paidDate: String = "", val note: String = "", val paidAmount: Double = 0.0, val payments: List<Payment> = emptyList(), val phone: String = "", val location: String = "", val photoUri: String = "", val photoData: String = "", val additions: List<DebtAddition> = emptyList(), val transactions: List<DebtTransaction> = emptyList())
private fun remaining(debt: Debt): Double = (debt.amount - debt.paidAmount).coerceAtLeast(0.0)
private fun initialDebtAmount(debt: Debt): Double = (debt.amount - debt.additions.sumOf { it.amount }).coerceAtLeast(0.0)
private fun orderedTransactions(debt: Debt): List<DebtTransaction> {
    if (debt.transactions.isNotEmpty()) return debt.transactions
    val legacy = mutableListOf<DebtTransaction>()
    debt.payments.forEach { legacy.add(DebtTransaction("payment", it.amount, it.date)) }
    debt.additions.forEach { legacy.add(DebtTransaction("addition", it.amount, it.date)) }
    return legacy.sortedBy { it.date }
}
private fun today(): String = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
private const val PREFS_NAME = "debt_book_local"
private const val PREFS_KEY = "debts_json"
private const val PREFS_KEY_PREVIOUS = "debts_json_previous"
private const val BACKUP_FILE_NAME = "ديون احتياط.json"
private const val PREFS_BACKUP_HASH = "last_external_backup_hash"
private val Pink = Color(0xFFE91E63)
private val SoftPink = Color(0xFFFFE4EE)
private val Green = Color(0xFF18A66A)

class MainActivity : ComponentActivity() {
    var latestDebts: List<Debt> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        latestDebts = loadLocalDebts(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveLocalDebts(this@MainActivity, latestDebts)
                saveBackup(this@MainActivity, latestDebts)
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
        setContent { DebtBookApp() }
    }

    override fun onStop() {
        super.onStop()
        saveLocalDebts(this, latestDebts)
        saveBackup(this, latestDebts)
    }
}

@Composable
fun DebtBookApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    var debts by remember { mutableStateOf(loadLocalDebts(activity)) }
    var loaded by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                activity.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(debtsToJson(debts)) }
                Toast.makeText(activity, "تم حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(activity, "تعذر حفظ النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val imported = importDebtsFromUri(activity, uri)
            if (imported != null) {
                debts = imported
                Toast.makeText(activity, "تم استيراد دفتر الديون بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "تعذر قراءة ملف النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(Unit) { loaded = true; delay(2200); showSplash = false }
    LaunchedEffect(debts, loaded) {
        if (loaded) {
            activity.latestDebts = debts
            saveLocalDebts(activity, debts)
        }
    }
    if (showSplash) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFFFFC4D7)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = com.blusher.cosmetics.R.drawable.splash_reference),
                contentDescription = "شاشة البداية",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .width(112.dp)
                    .height(3.dp),
                color = Color(0xFFC00060),
                trackColor = Color(0x33FFFFFF)
            )
        }
        return
    }

    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Debt?>(null) }
    BackHandler(enabled = showAdd || selected != null) {
        if (showAdd) showAdd = false else selected = null
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF8E4A63), secondary = Color(0xFFD79AAF), background = Color(0xFFFFF8FA))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8FA)) {
            when {
                showAdd -> AddDebtScreen(
                    onBack = { showAdd = false },
                    onSave = { person, amount, date, note, phone, location, photoData ->
                        debts = debts + Debt(System.currentTimeMillis(), person, amount, date, note = note, phone = phone, location = location, photoData = photoData)
                        showAdd = false
                    }
                )
                selected != null -> DebtDetailsScreen(
                    debt = selected!!,
                    activity = activity,
                    onBack = { selected = null },
                    onPayment = { payment, paymentDate ->
                        val current = selected!!
                        val newPaid = (current.paidAmount + payment).coerceAtMost(current.amount)
                        val updated = current.copy(
                            paidAmount = newPaid,
                            paidDate = if (newPaid >= current.amount) paymentDate else "",
                            payments = current.payments + Payment(payment, paymentDate),
                            transactions = orderedTransactions(current) + DebtTransaction("payment", payment, paymentDate)
                        )
                        debts = debts.map { if (it.id == current.id) updated else it }
                        selected = updated
                    },
                    onAddDebt = { extraDebt, extraDebtDate ->
                        val current = selected!!
                        val updated = current.copy(
                            amount = current.amount + extraDebt,
                            paidDate = "",
                            additions = current.additions + DebtAddition(extraDebt, extraDebtDate),
                            transactions = orderedTransactions(current) + DebtTransaction("addition", extraDebt, extraDebtDate)
                        )
                        debts = debts.map { if (it.id == current.id) updated else it }
                        selected = updated
                    },
                    onDelete = {
                        debts = debts.filterNot { it.id == selected!!.id }
                        selected = null
                    }
                )
                else -> HomeScreen(debts, { showAdd = true }, { selected = it }, activity, { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }, { exportBackupLauncher.launch("ديون احتياط.json") })
            }
        }
    }
}

@Composable
fun HomeScreen(debts: List<Debt>, onAdd: () -> Unit, onOpen: (Debt) -> Unit, activity: MainActivity, onImport: () -> Unit, onExportBackup: () -> Unit) {
    val totalOriginal = debts.sumOf { it.amount }
    val totalPaid = debts.sumOf { it.paidAmount }
    val totalRemaining = debts.sumOf { remaining(it) }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF9FB)).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var backupMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { backupMenu = true }) { Icon(Icons.Default.Menu, "النسخ الاحتياطي", tint = Pink) }
                DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                    DropdownMenuItem(text = { Text("حفظ نسخة احتياطية") }, onClick = { backupMenu = false; onExportBackup() })
                    DropdownMenuItem(text = { Text("استيراد نسخة احتياطية") }, onClick = { backupMenu = false; onImport() })
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("دفتر الديون", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF8B2147))
                Text("إدارة الديون والتسديدات", color = Color.Gray, fontSize = 13.sp)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.AddCircle, "إضافة", tint = Pink, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("إجمالي الديون", totalOriginal, Pink, Modifier.weight(1f))
            SummaryCard("المتبقي", totalRemaining, Color(0xFFE64A5F), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("المبالغ المسددة", totalPaid, Green, Modifier.weight(1f))
            SummaryCard("غير المسددين", debts.count { remaining(it) > 0 }.toDouble(), Color(0xFFB44B76), Modifier.weight(1f), false)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("أحدث الديون", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { exportAllDebts(activity, debts) }) { Text("تصدير الكل", color = Pink) }
        }
        if (debts.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("ماكو ديون مسجلة حالياً", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(debts.reversed(), key = { it.id }) { DebtCard(it, onOpen) }
            }
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة دين جديد", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: Double, accent: Color, modifier: Modifier = Modifier, money: Boolean = true) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (accent == Green) Color(0xFFE5F7EF) else SoftPink)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.DarkGray, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text(if (money) String.format(Locale.US, "%.0f د.ع", value) else String.format(Locale.US, "%.0f", value), color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        }
    }
}

@Composable
fun DebtCard(debt: Debt, onOpen: (Debt) -> Unit) {
    Card(onClick = { onOpen(debt) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (debt.paidDate.isEmpty()) Icons.Default.Person else Icons.Default.CheckCircle, null,
                tint = if (debt.paidDate.isEmpty()) Pink else Green, modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(debt.person, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("تاريخ الدين: ${debt.debtDate}", color = Color.Gray)
                if (debt.paidDate.isNotEmpty()) Text("تم التسديد: ${debt.paidDate}", color = Color(0xFF4C8A63))
            }
            Text(String.format(Locale.US, "%.0f د.ع", remaining(debt)), fontWeight = FontWeight.Bold, color = if (remaining(debt) > 0) Pink else Green)
        }
    }
}

@Composable
fun AddDebtScreen(onBack: () -> Unit, onSave: (String, Double, String, String, String, String, String) -> Unit) {
    var person by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var photoData by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoData = encodeCustomerPhoto(context, uri) ?: ""
            if (photoData.isBlank()) Toast.makeText(context, "تعذر قراءة الصورة", Toast.LENGTH_SHORT).show()
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF9FB)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") }
            Text("إضافة زبون", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.size(86.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = SoftPink)) {
                        Icon(if (photoData.isBlank()) Icons.Default.CameraAlt else Icons.Default.CheckCircle, "إضافة صورة", tint = Pink, modifier = Modifier.size(38.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(if (photoData.isBlank()) "إضافة صورة (اختياري)" else "تم حفظ الصورة", color = if (photoData.isBlank()) Color.Gray else Green)
                }
            }
            item { OutlinedTextField(person, { person = it }, Modifier.fillMaxWidth(), label = { Text("اسم الزبون *") }, leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true) }
            item { OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("رقم الهاتف") }, leadingIcon = { Icon(Icons.Default.Phone, null) }, singleLine = true) }
            item { OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("الموقع") }, leadingIcon = { Icon(Icons.Default.LocationOn, null) }, singleLine = true) }
            item { OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة") }, leadingIcon = { Icon(Icons.Default.EditNote, null) }, minLines = 3) }
            item { HorizontalDivider(); Text("بيانات الدين", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Pink) }
            item { OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("مبلغ الدين *") }, singleLine = true) }
            item { OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("تاريخ الدين") }, singleLine = true) }
        }
        Button(onClick = {
            val value = amount.toDoubleOrNull() ?: 0.0
            if (person.isNotBlank() && value > 0) onSave(person.trim(), value, date.ifBlank { today() }, note.trim(), phone.trim(), location.trim(), photoData)
            else Toast.makeText(context, "أدخل اسم الزبون ومبلغ الدين", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
            Text("حفظ", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DebtDetailsScreen(debt: Debt, activity: MainActivity, onBack: () -> Unit, onPayment: (Double, String) -> Unit, onAddDebt: (Double, String) -> Unit, onDelete: () -> Unit) {
    var paymentText by remember(debt.id) { mutableStateOf("") }
    var paymentDate by remember(debt.id) { mutableStateOf(today()) }
    var newDebtText by remember(debt.id) { mutableStateOf("") }
    var newDebtDate by remember(debt.id) { mutableStateOf(today()) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF9FB)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") }
            Text("تفاصيل الزبون", fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { exportSingleDebt(activity, debt) }) { Icon(Icons.Default.Share, "مشاركة", tint = Pink) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val customerBitmap = remember(debt.photoData) { decodeCustomerPhoto(debt.photoData) }
                        if (customerBitmap != null) {
                            Image(customerBitmap.asImageBitmap(), "صورة الزبون", modifier = Modifier.size(92.dp), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.AccountCircle, null, tint = Pink, modifier = Modifier.size(82.dp))
                        }
                        Text(debt.person, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                        if (debt.phone.isNotBlank()) Text(debt.phone, color = Color.Gray)
                        if (debt.location.isNotBlank()) Text(debt.location, color = Color.Gray)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CustomerAmountCard("إجمالي الدين", debt.amount, Pink, Modifier.weight(1f))
                            CustomerAmountCard("المسدد", debt.paidAmount, Green, Modifier.weight(1f))
                            CustomerAmountCard("المتبقي", remaining(debt), Color(0xFFE64A5F), Modifier.weight(1f))
                        }
                        if (debt.note.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("ملاحظة: " + debt.note, modifier = Modifier.fillMaxWidth(), color = Color.DarkGray)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سجل المعاملات", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("مرتبة حسب تسلسل الإدخال", fontSize = 12.sp, color = Color.Gray)
                    }
                    Surface(shape = RoundedCornerShape(50), color = SoftPink) {
                        Text(
                            (orderedTransactions(debt).size + 1).toString() + " عملية",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = Pink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Card(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = SoftPink) {
                            Text("1", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Pink, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("الدين الأصلي", color = Pink, fontWeight = FontWeight.Bold)
                            Text(String.format(Locale.US, "%.0f د.ع", initialDebtAmount(debt)), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text("التاريخ: " + debt.debtDate, color = Color.Gray, fontSize = 12.sp)
                        }
                        Text("البداية", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            val transactionLog = orderedTransactions(debt)
            items(transactionLog.indices.toList()) { index ->
                val tx = transactionLog[index]
                val throughNow = transactionLog.take(index + 1)
                val addedSoFar = throughNow.filter { it.type == "addition" }.sumOf { it.amount }
                val paidSoFar = throughNow.filter { it.type == "payment" }.sumOf { it.amount }
                val after = (initialDebtAmount(debt) + addedSoFar - paidSoFar).coerceAtLeast(0.0)
                val isAddition = tx.type == "addition"
                val accent = if (isAddition) Pink else Green
                val cardBg = if (isAddition) Color(0xFFFFF0F5) else Color(0xFFF0FAF5)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = cardBg) {
                            Text(
                                (index + 2).toString(),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isAddition) Icons.Default.AddCircle else Icons.Default.Payments,
                                    null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isAddition) "إضافة دين" else "تسديد", color = accent, fontWeight = FontWeight.Bold)
                            }
                            Text(String.format(Locale.US, "%.0f د.ع", tx.amount), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text("التاريخ: " + tx.date, color = Color.Gray, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("المتبقي", color = Color.Gray, fontSize = 11.sp)
                            Text(String.format(Locale.US, "%.0f د.ع", after), color = Color(0xFF7A263F), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
            if (remaining(debt) > 0) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2FBF7))) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payments, null, tint = Green)
                                Spacer(Modifier.width(8.dp))
                                Text("تسديد الدين", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Green)
                            }
                            OutlinedTextField(paymentText, { paymentText = it }, Modifier.fillMaxWidth(), label = { Text("مبلغ التسديد (د.ع)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(paymentDate, { paymentDate = it }, Modifier.fillMaxWidth(), label = { Text("تاريخ التسديد") }, singleLine = true)
                            Button(onClick = {
                                val value = paymentText.toDoubleOrNull() ?: 0.0
                                if (value > 0 && value <= remaining(debt)) { onPayment(value, paymentDate.ifBlank { today() }); paymentText = "" }
                                else Toast.makeText(activity, "أدخل مبلغ صحيح لا يتجاوز الباقي", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                                Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("تأكيد التسديد", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3F7))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddCircle, null, tint = Pink)
                            Spacer(Modifier.width(8.dp))
                            Text("إضافة دين", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Pink)
                        }
                        OutlinedTextField(newDebtText, { newDebtText = it }, Modifier.fillMaxWidth(), label = { Text("مبلغ الدين الإضافي (د.ع)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(newDebtDate, { newDebtDate = it }, Modifier.fillMaxWidth(), label = { Text("تاريخ إضافة الدين") }, singleLine = true)
                        Button(onClick = {
                            val value = newDebtText.toDoubleOrNull() ?: 0.0
                            if (value > 0) {
                                onAddDebt(value, newDebtDate.ifBlank { today() })
                                newDebtText = ""
                            } else {
                                Toast.makeText(activity, "أدخل مبلغ دين صحيح", Toast.LENGTH_SHORT).show()
                            }
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("تأكيد إضافة الدين", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("حذف الزبون والدين") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("تأكيد الحذف") },
            text = { Text("راح ينحذف الدين وكل سجل التسديدات لهذا الزبون. متأكد؟") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("حذف", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
    }
}

@Composable
private fun CustomerAmountCard(title: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (color == Green) Color(0xFFEAF8F1) else SoftPink)) {
        Column(Modifier.padding(vertical = 11.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Text(String.format(Locale.US, "%.0f", amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
            Text("د.ع", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

private fun shareBitmap(activity: MainActivity, bitmap: Bitmap, fileName: String) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DebtBook")
    }
    val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
    activity.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, "مشاركة صورة الدين"))
}

private fun makeDebtBitmap(debt: Debt): Bitmap {
    val log = orderedTransactions(debt)
    val width = 1080
    val height = 720 + log.size * 145 + if (debt.note.isNotBlank()) 90 else 0
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(35,35,35)
        textSize = 42f
        textAlign = Paint.Align.RIGHT
    }
    var y = 75f
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText("تفاصيل الدين", 980f, y, paint)
    y += 78
    paint.textSize = 50f
    canvas.drawText(debt.person, 980f, y, paint)
    y += 74
    paint.textSize = 36f
    canvas.drawText("الدين الأصلي: " + String.format(Locale.US, "%.0f د.ع", initialDebtAmount(debt)) + "   " + debt.debtDate, 980f, y, paint)
    y += 72

    var runningDebt = initialDebtAmount(debt)
    var runningPaid = 0.0
    log.forEachIndexed { index, tx ->
        val isAddition = tx.type == "addition"
        if (isAddition) runningDebt += tx.amount else runningPaid += tx.amount
        val balance = (runningDebt - runningPaid).coerceAtLeast(0.0)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = if (isAddition) android.graphics.Color.rgb(190, 35, 95) else android.graphics.Color.rgb(30, 145, 90)
        canvas.drawText(
            (index + 1).toString() + " - " + (if (isAddition) "إضافة دين: " else "تسديد: ") + String.format(Locale.US, "%.0f د.ع", tx.amount),
            980f, y, paint
        )
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = android.graphics.Color.rgb(75,75,75)
        canvas.drawText(tx.date, 360f, y, paint)
        y += 52
        paint.color = android.graphics.Color.rgb(35,35,35)
        canvas.drawText("المتبقي بعد العملية: " + String.format(Locale.US, "%.0f د.ع", balance), 980f, y, paint)
        y += 80
    }

    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.color = android.graphics.Color.rgb(35,35,35)
    canvas.drawText("إجمالي الدين: " + String.format(Locale.US, "%.0f د.ع", debt.amount), 980f, y, paint)
    y += 55
    canvas.drawText("إجمالي المسدد: " + String.format(Locale.US, "%.0f د.ع", debt.paidAmount), 980f, y, paint)
    y += 55
    canvas.drawText("المتبقي: " + String.format(Locale.US, "%.0f د.ع", remaining(debt)), 980f, y, paint)
    y += 65

    if (debt.note.isNotBlank()) {
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 31f
        canvas.drawText("ملاحظة: " + debt.note.take(70), 980f, y, paint)
    }

    paint.textSize = 28f
    paint.typeface = android.graphics.Typeface.DEFAULT
    paint.color = android.graphics.Color.rgb(90,90,90)
    canvas.drawText("دفتر الديون", 980f, height - 35f, paint)
    return bitmap
}
private fun exportSingleDebt(activity: MainActivity, debt: Debt) {
    shareBitmap(activity, makeDebtBitmap(debt), "debt_" + debt.id + ".png")
}

private fun exportAllDebts(activity: MainActivity, debts: List<Debt>) {
    if (debts.isEmpty()) return
    val width = 1400
    val rowHeight = 230
    val height = 380 + debts.size * rowHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35,35,35); textAlign = Paint.Align.RIGHT }
    val right = 1320f
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 52f
    canvas.drawText("سجل الديون الكامل", right, 75f, paint)
    paint.typeface = android.graphics.Typeface.DEFAULT
    paint.textSize = 34f
    canvas.drawText("العدد: " + debts.size + "    إجمالي الديون: " + String.format(Locale.US, "%.0f", debts.sumOf { it.amount }) + " د.ع", right, 135f, paint)
    canvas.drawText("المسدد: " + String.format(Locale.US, "%.0f", debts.sumOf { it.paidAmount }) + " د.ع    المتبقي: " + String.format(Locale.US, "%.0f", debts.sumOf { remaining(it) }) + " د.ع", right, 190f, paint)
    var y = 285f
    debts.forEachIndexed { index, debt ->
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 38f
        canvas.drawText((index + 1).toString() + ". " + debt.person, right, y, paint)
        y += 48f
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 31f
        canvas.drawText("الدين: " + String.format(Locale.US, "%.0f", debt.amount) + " د.ع    |    تاريخ الدين: " + debt.debtDate, right, y, paint)
        y += 43f
        canvas.drawText("المسدد: " + String.format(Locale.US, "%.0f", debt.paidAmount) + " د.ع    |    المتبقي: " + String.format(Locale.US, "%.0f", remaining(debt)) + " د.ع", right, y, paint)
        y += 43f
        val status = if (remaining(debt) <= 0.0) "مسدد بالكامل" else "غير مسدد بالكامل"
        val lastPayment = debt.payments.lastOrNull()?.date
        canvas.drawText(if (lastPayment != null) "الحالة: " + status + "    |    آخر تسديد: " + lastPayment else "الحالة: " + status + "    |    لا توجد تسديدات", right, y, paint)
        y += 43f
        if (debt.note.isNotBlank()) {
            val safeNote = if (debt.note.length > 55) debt.note.take(52) + "..." else debt.note
            canvas.drawText("ملاحظة: " + safeNote, right, y, paint)
        }
        paint.color = android.graphics.Color.rgb(225,225,225)
        canvas.drawLine(80f, y + 28f, right, y + 28f, paint)
        paint.color = android.graphics.Color.rgb(35,35,35)
        y += 53f
    }
    shareBitmap(activity, bitmap, "all_debts.png")
}




private fun encodeCustomerPhoto(context: Context, uri: Uri): String? {
    return try {
        val original = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val maxSide = 720
        val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height).toFloat())
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true) else original
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) { null }
}

private fun decodeCustomerPhoto(data: String): Bitmap? {
    if (data.isBlank()) return null
    return try { BitmapFactory.decodeByteArray(Base64.decode(data, Base64.DEFAULT), 0, Base64.decode(data, Base64.DEFAULT).size) } catch (_: Exception) { null }
}

private fun debtsToJson(debts: List<Debt>): String {
    return JSONArray().apply {
        debts.forEach { debt ->
            put(JSONObject().apply {
                put("id", debt.id); put("person", debt.person); put("amount", debt.amount)
                put("debtDate", debt.debtDate); put("paidDate", debt.paidDate); put("note", debt.note)
                put("paidAmount", debt.paidAmount); put("phone", debt.phone); put("location", debt.location); put("photoUri", debt.photoUri); put("photoData", debt.photoData)
                put("payments", JSONArray().apply {
                    debt.payments.forEach { p -> put(JSONObject().apply { put("amount", p.amount); put("date", p.date) }) }
                })
                put("additions", JSONArray().apply {
                    debt.additions.forEach { a -> put(JSONObject().apply { put("amount", a.amount); put("date", a.date) }) }
                })
                put("transactions", JSONArray().apply {
                    orderedTransactions(debt).forEach { t ->
                        put(JSONObject().apply { put("type", t.type); put("amount", t.amount); put("date", t.date) })
                    }
                })
            })
        }
    }.toString()
}

private fun parseDebtsArray(text: String): List<Debt> {
    val array = JSONArray(text)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        Debt(
            o.optLong("id", System.currentTimeMillis() + i), o.optString("person"), o.optDouble("amount", 0.0),
            o.optString("debtDate"), o.optString("paidDate"), o.optString("note"),
            o.optDouble("paidAmount", if (o.optString("paidDate").isNotEmpty()) o.optDouble("amount", 0.0) else 0.0),
            buildList {
                val ps = o.optJSONArray("payments")
                if (ps != null) for (j in 0 until ps.length()) {
                    val p = ps.getJSONObject(j); add(Payment(p.optDouble("amount", 0.0), p.optString("date")))
                }
            }, o.optString("phone"), o.optString("location"), o.optString("photoUri"), o.optString("photoData"),
            buildList {
                val additions = o.optJSONArray("additions")
                if (additions != null) for (j in 0 until additions.length()) {
                    val a = additions.getJSONObject(j); add(DebtAddition(a.optDouble("amount", 0.0), a.optString("date")))
                }
            },
            buildList {
                val transactions = o.optJSONArray("transactions")
                if (transactions != null) for (j in 0 until transactions.length()) {
                    val t = transactions.getJSONObject(j)
                    add(DebtTransaction(t.optString("type"), t.optDouble("amount", 0.0), t.optString("date")))
                }
            }
        )
    }
}

private fun loadLocalDebts(context: Context): List<Debt> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val primary = prefs.getString(PREFS_KEY, null)
    if (primary != null) try { return parseDebtsArray(primary) } catch (_: Exception) { }
    val previous = prefs.getString(PREFS_KEY_PREVIOUS, null)
    if (previous != null) try { return parseDebtsArray(previous) } catch (_: Exception) { }
    return emptyList()
}

private fun saveLocalDebts(context: Context, debts: List<Debt>): Boolean {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = debtsToJson(debts)
        val current = prefs.getString(PREFS_KEY, null)
        val editor = prefs.edit()
        if (current != null && current != next) {
            try { parseDebtsArray(current); editor.putString(PREFS_KEY_PREVIOUS, current) } catch (_: Exception) { }
        }
        editor.putString(PREFS_KEY, next).commit()
    } catch (_: Exception) { false }
}

private fun saveAppExternalBackup(activity: MainActivity, debts: List<Debt>) {
    try {
        val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
        if (!dir.exists()) dir.mkdirs()
        val target = java.io.File(dir, BACKUP_FILE_NAME)
        val temp = java.io.File(dir, BACKUP_FILE_NAME + ".tmp")
        val root = JSONObject().apply {
            put("version", 4); put("app", "دفتر الديون"); put("updatedAt", System.currentTimeMillis())
            put("debts", JSONArray(debtsToJson(debts)))
        }
        temp.writeText(root.toString(2), Charsets.UTF_8)
        if (target.exists()) target.delete()
        temp.renameTo(target)
    } catch (_: Exception) { }
}

private fun saveBackup(activity: MainActivity, debts: List<Debt>) {
    saveAppExternalBackup(activity, debts)
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
    try {
        val dataJson = debtsToJson(debts)
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dataHash = dataJson.hashCode().toString()
        if (prefs.getString(PREFS_BACKUP_HASH, null) == dataHash) return

        val resolver = activity.contentResolver
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "ديون احتياط_" + stamp + ".json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DebtBook")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: return
        val root = JSONObject().apply {
            put("version", 4)
            put("app", "دفتر الديون")
            put("updatedAt", System.currentTimeMillis())
            put("debts", JSONArray(dataJson))
        }
        resolver.openOutputStream(uri, "w")?.use {
            it.write(root.toString(2).toByteArray(Charsets.UTF_8))
            it.flush()
        } ?: run {
            resolver.delete(uri, null, null)
            return
        }
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }, null, null)
        prefs.edit().putString(PREFS_BACKUP_HASH, dataHash).commit()
    } catch (_: Exception) {
        // External backup must never interfere with the primary internal ledger.
    }
}

private fun importDebtsFromUri(activity: MainActivity, uri: Uri): List<Debt>? {
    return try {
        val text = activity.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
        val root = JSONObject(text)
        val array = root.optJSONArray("debts") ?: return null
        val parsed = parseDebtsArray(array.toString())
        if (parsed.any { it.person.isBlank() || it.amount < 0.0 || it.paidAmount < 0.0 || it.paidAmount > it.amount }) return null
        parsed
    } catch (_: Exception) { null }
}
