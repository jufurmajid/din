package com.blusher.cosmetics

import android.os.Bundle
import android.content.ContentValues
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
import androidx.compose.foundation.layout.*
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DebtBookApp() }
    }
}

@Composable
fun DebtBookApp() {
    val activity = androidx.compose.ui.platform.LocalContext.current as MainActivity
    var debts by remember { mutableStateOf(listOf<Debt>()) }
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
    LaunchedEffect(Unit) {
        // Start safely with an empty ledger. Backup access is done only when the user requests restore.
        loaded = true
    }
    LaunchedEffect(debts, loaded) {
        // Automatic MediaStore backup is temporarily disabled to prevent startup/device-specific crashes.
    }
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Debt?>(null) }
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
    val total = debts.sumOf { remaining(it) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("دفتر الديون", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("سجل ديونك ومدفوعاتك بسهولة", color = Color.Gray)
            }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "إضافة دين") }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF2DCE4))) {
            Column(Modifier.padding(20.dp)) {
                Text("إجمالي الديون المتبقية", color = Color.DarkGray)
                Text(String.format(Locale.US, "%.0f د.ع", total), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${debts.count { remaining(it) > 0 }} دين غير مسدد")
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { exportAllDebts(activity, debts) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("تصدير كل الديون كصورة")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("استعادة نسخة من ملف الجهاز")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة دين جديد")
        }
        Spacer(Modifier.height(16.dp))
        Text("سجل الديون", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (debts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ماكو ديون مسجلة حالياً", color = Color.Gray) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(debts.reversed(), key = { it.id }) { debt -> DebtCard(debt, onOpen) }
        }
    }
}

@Composable
fun DebtCard(debt: Debt, onOpen: (Debt) -> Unit) {
    Card(onClick = { onOpen(debt) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (debt.paidDate.isEmpty()) Icons.Default.Person else Icons.Default.CheckCircle, null,
                tint = if (debt.paidDate.isEmpty()) Color(0xFF8E4A63) else Color(0xFF4C8A63), modifier = Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(debt.person, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("تاريخ الدين: ${debt.debtDate}", color = Color.Gray)
                if (debt.paidDate.isNotEmpty()) Text("تم التسديد: ${debt.paidDate}", color = Color(0xFF4C8A63))
            }
            Text(String.format(Locale.US, "%.0f د.ع", remaining(debt)), fontWeight = FontWeight.Bold, color = Color(0xFF8E4A63))
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
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(debt.person, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("المبلغ", color = Color.Gray)
                Text(String.format(Locale.US, "%.0f د.ع", remaining(debt)), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E4A63))
                Text("أصل الدين: " + String.format(Locale.US, "%.0f د.ع", debt.amount), color = Color.Gray)
                if (debt.paidAmount > 0) Text("المسدد: " + String.format(Locale.US, "%.0f د.ع", debt.paidAmount), color = Color(0xFF4C8A63))
                Text("المتبقي: " + String.format(Locale.US, "%.0f د.ع", remaining(debt)), fontWeight = FontWeight.Bold)
                if (debt.payments.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("سجل التسديدات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    var runningPaid = 0.0
                    debt.payments.forEach { p ->
                        runningPaid += p.amount
                        val after = (debt.amount - runningPaid).coerceAtLeast(0.0)
                        Spacer(Modifier.height(8.dp))
                        Text("تم تسديد " + String.format(Locale.US, "%.0f د.ع", p.amount) + "  " + p.date, color = Color(0xFF4C8A63), fontWeight = FontWeight.Bold)
                        if (after > 0) Text("الباقي " + String.format(Locale.US, "%.0f د.ع", after), fontWeight = FontWeight.Bold)
                        else Text("تم التسديد بالكامل", color = Color(0xFF4C8A63), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("تاريخ الدين: ${debt.debtDate}")
                if (debt.paidDate.isNotEmpty()) Text("تاريخ التسديد: ${debt.paidDate}", color = Color(0xFF4C8A63))
                else Text("الحالة: غير مسدد", color = Color(0xFFB04B4B))
                if (debt.note.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text("الملاحظة: ${debt.note}") }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { exportSingleDebt(activity, debt) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("تصدير الدين كصورة ومشاركة") }
        Spacer(Modifier.height(8.dp))
        if (remaining(debt) > 0) {
            OutlinedTextField(
                value = paymentText,
                onValueChange = { paymentText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("مبلغ التسديد") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(paymentDate, { paymentDate = it }, Modifier.fillMaxWidth(), label = { Text("تاريخ التسديد") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val value = paymentText.toDoubleOrNull() ?: 0.0
                    if (value > 0 && value <= remaining(debt)) {
                        onPayment(value, paymentDate.ifBlank { today() })
                        paymentText = ""
                    } else {
                        Toast.makeText(activity, "أدخل مبلغ صحيح لا يتجاوز الباقي", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) { Text("تسجيل التسديد") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("حذف الدين") }
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



private const val BACKUP_FILE_NAME = "debt_book_backup.json"

private fun saveBackup(activity: MainActivity, debts: List<Debt>) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
    val resolver = activity.contentResolver
    val existingUri = findBackupUri(activity)
    val uri = existingUri ?: resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
        put(MediaStore.Downloads.MIME_TYPE, "application/json")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DebtBook")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }) ?: return
    try {
        val root = JSONObject().apply {
            put("version", 1)
            put("app", "دفتر الديون")
            put("updatedAt", System.currentTimeMillis())
            put("debts", JSONArray().apply {
                debts.forEach { debt ->
                    put(JSONObject().apply {
                        put("id", debt.id)
                        put("person", debt.person)
                        put("amount", debt.amount)
                        put("debtDate", debt.debtDate)
                        put("paidDate", debt.paidDate)
                        put("note", debt.note)
                        put("paidAmount", debt.paidAmount)
                        put("payments", JSONArray().apply { debt.payments.forEach { p -> put(JSONObject().apply { put("amount", p.amount); put("date", p.date) }) } })
                    })
                }
            })
        }
        resolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    } catch (_: Exception) { }
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
