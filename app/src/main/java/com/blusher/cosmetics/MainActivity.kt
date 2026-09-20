package com.blusher.cosmetics

import android.os.Bundle
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

data class Debt(val id: Long, val person: String, val amount: Double, val debtDate: String, val paidDate: String = "", val note: String = "")
private fun today(): String = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DebtBookApp() }
    }
}

@Composable
fun DebtBookApp() {
    var debts by remember { mutableStateOf(listOf<Debt>()) }
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
                    onBack = { selected = null },
                    onPaid = {
                        debts = debts.map { if (it.id == selected!!.id) it.copy(paidDate = today()) else it }
                        selected = null
                    },
                    onDelete = {
                        debts = debts.filterNot { it.id == selected!!.id }
                        selected = null
                    }
                )
                else -> HomeScreen(debts, { showAdd = true }, { selected = it })
            }
        }
    }
}

@Composable
fun HomeScreen(debts: List<Debt>, onAdd: () -> Unit, onOpen: (Debt) -> Unit) {
    val total = debts.filter { it.paidDate.isEmpty() }.sumOf { it.amount }
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
                Text("${debts.count { it.paidDate.isEmpty() }} دين غير مسدد")
            }
        }
        Spacer(Modifier.height(16.dp))
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
            Text(String.format(Locale.US, "%.0f د.ع", debt.amount), fontWeight = FontWeight.Bold, color = Color(0xFF8E4A63))
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
fun DebtDetailsScreen(debt: Debt, onBack: () -> Unit, onPaid: () -> Unit, onDelete: () -> Unit) {
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
                Text(String.format(Locale.US, "%.0f د.ع", debt.amount), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8E4A63))
                Spacer(Modifier.height(12.dp))
                Text("تاريخ الدين: ${debt.debtDate}")
                if (debt.paidDate.isNotEmpty()) Text("تاريخ التسديد: ${debt.paidDate}", color = Color(0xFF4C8A63))
                else Text("الحالة: غير مسدد", color = Color(0xFFB04B4B))
                if (debt.note.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text("الملاحظة: ${debt.note}") }
            }
        }
        Spacer(Modifier.weight(1f))
        if (debt.paidDate.isEmpty()) {
            Button(onClick = onPaid, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("تسجيل التسديد اليوم") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("حذف الدين") }
    }
}
