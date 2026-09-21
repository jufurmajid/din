package com.blusher.cosmetics

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class Payment(val amount: Double, val date: String)
data class Debt(val id: Long, val person: String, val amount: Double, val debtDate: String, val paidDate: String = "", val note: String = "", val paidAmount: Double = 0.0, val payments: List<Payment> = emptyList())
private fun remaining(debt: Debt): Double = (debt.amount - debt.paidAmount).coerceAtLeast(0.0)
private fun today(): String = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
private const val PREFS_NAME = "debt_book_local"
private const val PREFS_KEY = "debts_json"
private const val BACKUP_FILE_NAME = "ديون احتياط.json"
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
    LaunchedEffect(Unit) { loaded = true }
    LaunchedEffect(debts, loaded) {
        if (loaded) {
            activity.latestDebts = debts
            saveLocalDebts(activity, debts)
        }
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
                    onSave = { person, amount, date, note ->
                        debts = debts + Debt(System.currentTimeMillis(), person, amount, date, note = note)
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
                            payments = current.payments + Payment(payment, paymentDate)
                        )
                        debts = debts.map { if (it.id == current.id) updated else it }
                        selected = updated
                    },
                    onDelete = {
                        debts = debts.filterNot { it.id == selected!!.id }
                        selected = null
                    }
                )
                else -> HomeScreen(debts, { showAdd = true }, { selected = it }, activity, { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) })
            }
        }
    }
}

@Composable
fun HomeScreen(debts: List<Debt>, onAdd: () -> Unit, onOpen: (Debt) -> Unit, activity: MainActivity, onImport: () -> Unit) {
    val totalOriginal = debts.sumOf { it.amount }
    val totalPaid = debts.sumOf { it.paidAmount }
    val totalRemaining = debts.sumOf { remaining(it) }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF9FB)).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onImport) { Icon(Icons.Default.Menu, "النسخ الاحتياطي", tint = Pink) }
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
fun AddDebtScreen(onBack: () -> Unit, onSave: (String, Double, String, String) -> Unit) {
    var person by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today()) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") }
            Text("إضافة دين", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(person, { person = it }, Modifier.fillMaxWidth(), label = { Text("اسم الشخص") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("مبلغ الدين") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), label = { Text("تاريخ الدين") }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("ملاحظة") }, minLines = 3)
        Spacer(Modifier.weight(1f))
        Button(onClick = {
            val value = amount.toDoubleOrNull() ?: 0.0
            if (person.isNotBlank() && value > 0) onSave(person.trim(), value, date, note.trim())
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("حفظ الدين") }
    }
}

@Composable
fun DebtDetailsScreen(debt: Debt, activity: MainActivity, onBack: () -> Unit, onPayment: (Double, String) -> Unit, onDelete: () -> Unit) {
    var paymentText by remember(debt.id) { mutableStateOf("") }
    var paymentDate by remember(debt.id) { mutableStateOf(today()) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") }
            Text("تفاصيل الدين", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(debt.person, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("الدين: " + String.format(Locale.US, "%.0f د.ع", debt.amount) + "  •  " + debt.debtDate, color = Color.Gray)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("المسدد", color = Color.Gray); Text(String.format(Locale.US, "%.0f د.ع", debt.paidAmount), color = Color(0xFF4C8A63), fontWeight = FontWeight.Bold) }
                            Column(horizontalAlignment = Alignment.End) { Text("المتبقي", color = Color.Gray); Text(String.format(Locale.US, "%.0f د.ع", remaining(debt)), color = Color(0xFF8E4A63), fontWeight = FontWeight.Bold) }
                        }
                        if (debt.note.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("ملاحظة: " + debt.note) }
                    }
                }
            }
            if (debt.payments.isNotEmpty()) {
                item { Text("سجل التسديدات", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                items(debt.payments.indices.toList()) { index ->
                    val p = debt.payments[index]
                    val paidUntilHere = debt.payments.take(index + 1).sumOf { it.amount }
                    val after = (debt.amount - paidUntilHere).coerceAtLeast(0.0)
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("تم تسديد " + String.format(Locale.US, "%.0f د.ع", p.amount), color = Color(0xFF4C8A63), fontWeight = FontWeight.Bold)
                            Text("التاريخ: " + p.date, color = Color.Gray)
                            if (after > 0) Text("الباقي " + String.format(Locale.US, "%.0f د.ع", after), fontWeight = FontWeight.Bold)
                            else Text("تم التسديد بالكامل", color = Color(0xFF4C8A63), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                Button(onClick = { exportSingleDebt(activity, debt) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("تصدير التفاصيل كصورة")
                }
            }
            if (remaining(debt) > 0) {
                item { Text("تسجيل تسديد جديد", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(paymentText, { paymentText = it }, Modifier.fillMaxWidth(), label = { Text("مبلغ التسديد") }, singleLine = true) }
                item { OutlinedTextField(paymentDate, { paymentDate = it }, Modifier.fillMaxWidth(), label = { Text("تاريخ التسديد") }, singleLine = true) }
                item {
                    Button(onClick = {
                        val value = paymentText.toDoubleOrNull() ?: 0.0
                        if (value > 0 && value <= remaining(debt)) {
                            onPayment(value, paymentDate.ifBlank { today() })
                            paymentText = ""
                        } else Toast.makeText(activity, "أدخل مبلغ صحيح لا يتجاوز الباقي", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("حفظ التسديد") }
                }
            }
            item {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("حذف الدين") }
            }
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
    val width = 1080
    val height = 760 + debt.payments.size * 150
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(35,35,35)
        textSize = 42f
        textAlign = Paint.Align.RIGHT
    }
    var y = 80f
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText("تفاصيل الدين", 980f, y, paint)
    y += 85
    paint.textSize = 52f
    canvas.drawText(debt.person, 980f, y, paint)
    y += 80
    paint.textSize = 40f
    canvas.drawText("الدين " + String.format(Locale.US, "%.0f د.ع", debt.amount) + "  " + debt.debtDate, 980f, y, paint)
    y += 75
    paint.typeface = android.graphics.Typeface.DEFAULT
    if (debt.note.isNotBlank()) {
        canvas.drawText("ملاحظة: " + debt.note, 980f, y, paint)
        y += 70
    }
    var runningPaid = 0.0
    if (debt.payments.isEmpty()) {
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("الباقي " + String.format(Locale.US, "%.0f د.ع", debt.amount), 980f, y, paint)
    } else {
        debt.payments.forEach { p ->
            runningPaid += p.amount
            val after = (debt.amount - runningPaid).coerceAtLeast(0.0)
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("تم تسديد " + String.format(Locale.US, "%.0f د.ع", p.amount) + "  " + p.date, 980f, y, paint)
            y += 60
            if (after > 0) {
                canvas.drawText("الباقي " + String.format(Locale.US, "%.0f د.ع", after), 980f, y, paint)
            } else {
                paint.color = android.graphics.Color.rgb(40,120,70)
                canvas.drawText("تم التسديد بالكامل", 980f, y, paint)
                paint.color = android.graphics.Color.rgb(35,35,35)
            }
            y += 85
        }
    }
    paint.textSize = 30f
    paint.typeface = android.graphics.Typeface.DEFAULT
    canvas.drawText("دفتر الديون", 980f, height - 40f, paint)
    return bitmap
}
private fun exportSingleDebt(activity: MainActivity, debt: Debt) {
    shareBitmap(activity, makeDebtBitmap(debt), "debt_" + debt.id + ".png")
}

private fun exportAllDebts(activity: MainActivity, debts: List<Debt>) {
    if (debts.isEmpty()) return
    val rowHeight = 120
    val width = 1200
    val height = 260 + debts.size * rowHeight
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35,35,35); textSize = 42f }
    paint.textAlign = Paint.Align.RIGHT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText("سجل الديون الكامل", 1100f, 80f, paint)
    paint.typeface = android.graphics.Typeface.DEFAULT
    canvas.drawText("العدد: " + debts.size + "    الإجمالي: " + String.format(Locale.US, "%.0f", debts.sumOf { it.amount }) + " د.ع", 1100f, 150f, paint)
    var y = 250f
    debts.forEachIndexed { index, debt ->
        paint.textSize = 34f
        canvas.drawText((index + 1).toString() + ". " + debt.person, 1100f, y, paint)
        canvas.drawText(String.format(Locale.US, "%.0f", debt.amount) + " د.ع", 760f, y, paint)
        canvas.drawText("دين: " + debt.debtDate, 480f, y, paint)
        canvas.drawText(if (debt.paidDate.isBlank()) "غير مسدد" else "تسديد: " + debt.paidDate, 180f, y, paint)
        y += rowHeight
    }
    shareBitmap(activity, bitmap, "all_debts.png")
}




private fun debtsToJson(debts: List<Debt>): String {
    return JSONArray().apply {
        debts.forEach { debt ->
            put(JSONObject().apply {
                put("id", debt.id); put("person", debt.person); put("amount", debt.amount)
                put("debtDate", debt.debtDate); put("paidDate", debt.paidDate); put("note", debt.note)
                put("paidAmount", debt.paidAmount)
                put("payments", JSONArray().apply {
                    debt.payments.forEach { p -> put(JSONObject().apply { put("amount", p.amount); put("date", p.date) }) }
                })
            })
        }
    }.toString()
}

private fun loadLocalDebts(context: Context): List<Debt> {
    return try {
        val text = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREFS_KEY, null) ?: return emptyList()
        val array = JSONArray(text)
        List(array.length()) { i ->
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
                }
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun saveLocalDebts(context: Context, debts: List<Debt>) {
    try {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(PREFS_KEY, debtsToJson(debts)).commit()
    } catch (_: Exception) { }
}


private fun saveBackup(activity: MainActivity, debts: List<Debt>) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
    try {
        val resolver = activity.contentResolver
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "ديون احتياط_" + stamp + ".json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DebtBook")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }) ?: return
        val root = JSONObject().apply {
            put("version", 2)
            put("app", "دفتر الديون")
            put("updatedAt", System.currentTimeMillis())
            put("debts", JSONArray(debtsToJson(debts)))
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
    } catch (_: Exception) {
        // External backup must never interfere with the primary internal ledger.
    }
}

private fun findBackupUri(activity: MainActivity): Uri? {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
    val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
    activity.contentResolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        projection,
        "\${MediaStore.Downloads.DISPLAY_NAME}=?",
        arrayOf(BACKUP_FILE_NAME),
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
        }
    }
    return null
}

private fun loadBackup(activity: MainActivity): List<Debt> {
    val uri = findBackupUri(activity) ?: return emptyList()
    return try {
        val text = activity.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return emptyList()
        val array = JSONObject(text).optJSONArray("debts") ?: return emptyList()
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Debt(
                o.optLong("id", System.currentTimeMillis() + i),
                o.optString("person"),
                o.optDouble("amount", 0.0),
                o.optString("debtDate"),
                o.optString("paidDate"),
                o.optString("note"),
                o.optDouble("paidAmount", if (o.optString("paidDate").isNotEmpty()) o.optDouble("amount", 0.0) else 0.0),
                buildList { val ps=o.optJSONArray("payments"); if(ps!=null) for(j in 0 until ps.length()){ val p=ps.getJSONObject(j); add(Payment(p.optDouble("amount",0.0),p.optString("date"))) } }
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun importDebtsFromUri(activity: MainActivity, uri: Uri): List<Debt>? {
    return try {
        val text = activity.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return null
        val array = JSONObject(text).optJSONArray("debts") ?: return null
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Debt(
                o.optLong("id", System.currentTimeMillis() + i),
                o.optString("person"),
                o.optDouble("amount", 0.0),
                o.optString("debtDate"),
                o.optString("paidDate"),
                o.optString("note"),
                o.optDouble("paidAmount", if (o.optString("paidDate").isNotEmpty()) o.optDouble("amount", 0.0) else 0.0),
                buildList { val ps=o.optJSONArray("payments"); if(ps!=null) for(j in 0 until ps.length()){ val p=ps.getJSONObject(j); add(Payment(p.optDouble("amount",0.0),p.optString("date"))) } }
            )
        }
    } catch (_: Exception) { null }
}
