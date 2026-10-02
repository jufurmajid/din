from pathlib import Path
import re

path = Path("app/src/main/java/com/blusher/cosmetics/MainActivity.kt")
text = path.read_text(encoding="utf-8")

# New application package / namespace references.
text = text.replace("package com.blusher.cosmetics", "package com.jafarmajid.debtbook")
text = text.replace("com.blusher.cosmetics.R", "com.jafarmajid.debtbook.R")

if "import androidx.compose.ui.graphics.Brush" not in text:
    text = text.replace(
        "import androidx.compose.ui.graphics.Color\n",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush\n",
    )

old_colors = '''private val Pink = Color(0xFFE91E63)
private val SoftPink = Color(0xFFFFE4EE)
private val Green = Color(0xFF18A66A)'''
new_colors = '''private val PrimaryBlue = Color(0xFF2563EB)
private val Navy = Color(0xFF0B1220)
private val Navy2 = Color(0xFF102A56)
private val Gold = Color(0xFFF4B942)
private val Green = Color(0xFF10B981)
private val GreenSoft = Color(0xFFE8F8F1)
private val RedAccent = Color(0xFFDC2626)
private val RedSoft = Color(0xFFFEECEC)
private val BlueSoft = Color(0xFFEAF2FF)
private val AppBackground = Color(0xFFF5F7FB)
private val CardSurface = Color(0xFFFFFFFF)
private val Pink = PrimaryBlue
private val SoftPink = BlueSoft'''
if old_colors not in text:
    raise SystemExit("color block not found")
text = text.replace(old_colors, new_colors)

splash_re = re.compile(
    r'''    if \(showSplash\) \{.*?        return\n    \}\n\n    var showAdd''',
    re.S,
)
new_splash = '''    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Navy, Navy2, PrimaryBlue)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(34.dp),
                    color = Color(0xFF13233D),
                    shadowElevation = 18.dp
                ) {
                    Box(
                        modifier = Modifier.size(132.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(72.dp)
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            shape = RoundedCornerShape(50),
                            color = Green
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(7.dp).size(24.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "دفتر ديون",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "إدارة ديون الزبائن بسهولة واحترافية",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(34.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Gold,
                    trackColor = Color.White.copy(alpha = 0.16f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "سريع • بدون إعلانات • يعمل بدون إنترنت",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 12.sp
                )
            }
            Text(
                "بياناتك تبقى على جهازك",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
        return
    }

    var showAdd'''
text, count = splash_re.subn(new_splash, text, count=1)
if count != 1:
    raise SystemExit(f"splash block replacement failed: {count}")

old_theme = '''    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF8E4A63),
            secondary = Color(0xFFD79AAF),
            background = Color(0xFFFFF8FA)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFFFF8FA)) {'''
new_theme = '''    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryBlue,
            secondary = Gold,
            background = AppBackground,
            surface = CardSurface,
            error = RedAccent
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = AppBackground) {'''
if old_theme not in text:
    raise SystemExit("theme block not found")
text = text.replace(old_theme, new_theme)

old_header = '''        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            var backupMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { backupMenu = true }) {
                    Icon(Icons.Default.Menu, "النسخ الاحتياطي", tint = Pink)
                }
                DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("حفظ / تحديث النسخة الاحتياطية") },
                        onClick = { backupMenu = false; onSaveBackup() }
                    )
                    DropdownMenuItem(
                        text = { Text("استيراد نسخة احتياطية") },
                        onClick = { backupMenu = false; onImport() }
                    )
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("دفتر الديون", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF8B2147))
                Text("إدارة الديون والتسديدات", color = Color.Gray, fontSize = 13.sp)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.AddCircle, "إضافة", tint = Pink, modifier = Modifier.size(32.dp))
            }
        }'''
new_header = '''        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Navy),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var backupMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { backupMenu = true }) {
                        Icon(Icons.Default.Menu, "النسخ الاحتياطي", tint = Color.White)
                    }
                    DropdownMenu(expanded = backupMenu, onDismissRequest = { backupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("حفظ / تحديث النسخة الاحتياطية") },
                            onClick = { backupMenu = false; onSaveBackup() }
                        )
                        DropdownMenuItem(
                            text = { Text("استيراد نسخة احتياطية") },
                            onClick = { backupMenu = false; onImport() }
                        )
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("دفتر ديون", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("سجل مرتب • متابعة واضحة", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                }
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.AddCircle, "إضافة", tint = Gold, modifier = Modifier.size(34.dp))
                }
            }
        }'''
if old_header not in text:
    raise SystemExit("home header block not found")
text = text.replace(old_header, new_header)

summary_re = re.compile(
    r'''@Composable\nprivate fun SummaryCard\(.*?\n\}\n\n@Composable\nfun DebtCard''',
    re.S,
)
new_summary = '''@Composable
private fun SummaryCard(title: String, value: Double, accent: Color, modifier: Modifier = Modifier, money: Boolean = true) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(accent))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, color = Color(0xFF64748B), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (money) String.format(Locale.US, "%,.0f د.ع", value) else String.format(Locale.US, "%.0f", value),
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp
                )
            }
        }
    }
}

@Composable
fun DebtCard'''
text, count = summary_re.subn(new_summary, text, count=1)
if count != 1:
    raise SystemExit(f"SummaryCard replacement failed: {count}")

debt_card_re = re.compile(
    r'''fun DebtCard\(debt: Debt, onOpen: \(Debt\) -> Unit\) \{.*?\n\}\n\n@Composable\nfun AddDebtScreen''',
    re.S,
)
new_debt_card = '''fun DebtCard(debt: Debt, onOpen: (Debt) -> Unit) {
    val due = remaining(debt)
    val isPaid = due <= 0.0
    Card(
        onClick = { onOpen(debt) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isPaid) GreenSoft else BlueSoft
            ) {
                Icon(
                    if (isPaid) Icons.Default.CheckCircle else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isPaid) Green else PrimaryBlue,
                    modifier = Modifier.padding(10.dp).size(28.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(debt.person, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Navy)
                if (debt.phone.isNotBlank()) Text(debt.phone, color = Color(0xFF64748B), fontSize = 12.sp)
                Text("تاريخ الدين: ${debt.debtDate}", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (isPaid) "مُسدّد" else "المتبقي", color = if (isPaid) Green else Color(0xFF64748B), fontSize = 11.sp)
                Text(
                    String.format(Locale.US, "%,.0f د.ع", due),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isPaid) Green else PrimaryBlue,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun AddDebtScreen'''
text, count = debt_card_re.subn(new_debt_card, text, count=1)
if count != 1:
    raise SystemExit(f"DebtCard replacement failed: {count}")

# Global visual cleanup.
replacements = {
    "Color(0xFFFFF9FB)": "AppBackground",
    "Color(0xFFFFF8FA)": "AppBackground",
    "Color(0xFF8B2147)": "Navy",
    "Color(0xFFE64A5F)": "RedAccent",
    "Color(0xFFB44B76)": "Gold",
    "Color(0xFFFFF0F5)": "BlueSoft",
    "Color(0xFFFFF3F7)": "BlueSoft",
    "Color(0xFF7A263F)": "Navy",
    "Pictures/DebtBook": "Pictures/DaftarDeyoon",
    'Environment.DIRECTORY_DOWNLOADS + "/DebtBook/"': 'Environment.DIRECTORY_DOWNLOADS + "/DaftarDeyoon/"',
    'Text("دفتر الديون", fontSize = 25.sp': 'Text("دفتر ديون", fontSize = 25.sp',
}
for old, new in replacements.items():
    text = text.replace(old, new)

bitmap_re = re.compile(
    r'''private fun makeDebtBitmap\(debt: Debt\): Bitmap \{.*?\n\}\n\nprivate fun exportSingleDebt''',
    re.S,
)
new_bitmap = '''private fun makeDebtBitmap(debt: Debt): Bitmap {
    val log = orderedTransactions(debt)
    val width = 1080
    val height = 930 + log.size * 155 + if (debt.note.isNotBlank()) 80 else 0
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    canvas.drawColor(android.graphics.Color.rgb(245, 247, 251))

    paint.color = android.graphics.Color.rgb(11, 18, 32)
    canvas.drawRect(0f, 0f, width.toFloat(), 190f, paint)
    paint.textAlign = Paint.Align.RIGHT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 50f
    paint.color = android.graphics.Color.WHITE
    canvas.drawText("دفتر ديون", 980f, 72f, paint)
    paint.typeface = android.graphics.Typeface.DEFAULT
    paint.textSize = 28f
    paint.color = android.graphics.Color.rgb(205, 215, 232)
    canvas.drawText("كشف حساب دين مرتب وواضح", 980f, 122f, paint)
    paint.color = android.graphics.Color.rgb(244, 185, 66)
    canvas.drawRoundRect(80f, 150f, 1000f, 158f, 4f, 4f, paint)

    var y = 235f
    paint.color = android.graphics.Color.WHITE
    canvas.drawRoundRect(55f, y - 24f, 1025f, y + 190f, 24f, 24f, paint)
    paint.textAlign = Paint.Align.RIGHT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 46f
    paint.color = android.graphics.Color.rgb(11, 18, 32)
    canvas.drawText(debt.person, 970f, y + 35f, paint)
    paint.typeface = android.graphics.Typeface.DEFAULT
    paint.textSize = 30f
    paint.color = android.graphics.Color.rgb(100, 116, 139)
    canvas.drawText("تاريخ الدين: " + debt.debtDate, 970f, y + 83f, paint)
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 34f
    paint.color = android.graphics.Color.rgb(37, 99, 235)
    canvas.drawText("الدين الأصلي: " + String.format(Locale.US, "%,.0f د.ع", initialDebtAmount(debt)), 970f, y + 142f, paint)
    y += 250f

    var runningDebt = initialDebtAmount(debt)
    var runningPaid = 0.0
    log.forEachIndexed { index, tx ->
        val isAddition = tx.type == "addition"
        if (isAddition) runningDebt += tx.amount else runningPaid += tx.amount
        val balance = (runningDebt - runningPaid).coerceAtLeast(0.0)
        val accent = if (isAddition) android.graphics.Color.rgb(37, 99, 235) else android.graphics.Color.rgb(16, 185, 129)

        paint.color = android.graphics.Color.WHITE
        canvas.drawRoundRect(55f, y - 22f, 1025f, y + 112f, 20f, 20f, paint)
        paint.color = accent
        canvas.drawRoundRect(990f, y - 22f, 1025f, y + 112f, 12f, 12f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 32f
        paint.color = accent
        canvas.drawText((index + 1).toString() + " - " + (if (isAddition) "إضافة دين" else "تسديد") + ": " + String.format(Locale.US, "%,.0f د.ع", tx.amount), 950f, y + 28f, paint)
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 25f
        paint.color = android.graphics.Color.rgb(100, 116, 139)
        canvas.drawText("المتبقي بعد العملية: " + String.format(Locale.US, "%,.0f د.ع", balance), 950f, y + 75f, paint)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(tx.date, 92f, y + 50f, paint)
        y += 155f
    }

    paint.color = android.graphics.Color.rgb(234, 242, 255)
    canvas.drawRoundRect(55f, y - 5f, 1025f, y + 180f, 22f, 22f, paint)
    paint.textAlign = Paint.Align.RIGHT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 31f
    paint.color = android.graphics.Color.rgb(11, 18, 32)
    canvas.drawText("إجمالي الدين: " + String.format(Locale.US, "%,.0f د.ع", debt.amount), 955f, y + 48f, paint)
    paint.color = android.graphics.Color.rgb(16, 185, 129)
    canvas.drawText("إجمالي المسدد: " + String.format(Locale.US, "%,.0f د.ع", debt.paidAmount), 955f, y + 94f, paint)
    paint.color = android.graphics.Color.rgb(220, 38, 38)
    canvas.drawText("المتبقي: " + String.format(Locale.US, "%,.0f د.ع", remaining(debt)), 955f, y + 140f, paint)
    y += 230f

    if (debt.note.isNotBlank()) {
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 27f
        paint.color = android.graphics.Color.rgb(71, 85, 105)
        canvas.drawText("ملاحظة: " + debt.note.take(70), 970f, y, paint)
    }

    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 24f
    paint.typeface = android.graphics.Typeface.DEFAULT
    paint.color = android.graphics.Color.rgb(100, 116, 139)
    canvas.drawText("دفتر ديون • سجل واضح ومرتب", width / 2f, height - 32f, paint)
    return bitmap
}

private fun exportSingleDebt'''
text, count = bitmap_re.subn(new_bitmap, text, count=1)
if count != 1:
    raise SystemExit(f"makeDebtBitmap replacement failed: {count}")

# Final branding safety check: no old cosmetic brand remains in code.
leftovers = [x for x in ("blusher", "كوزمتك", "cosmetic") if x in text.lower()]
if leftovers:
    raise SystemExit("old branding remains: " + ", ".join(leftovers))

path.write_text(text, encoding="utf-8")
print("Professional rebrand patch applied successfully")
