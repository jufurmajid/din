from pathlib import Path
import re

main_path = Path("app/src/main/java/com/blusher/cosmetics/MainActivity.kt")
text = main_path.read_text(encoding="utf-8")

pattern = re.compile(
    r'''private fun exportAllDebts\(activity: MainActivity, debts: List<Debt>\) \{.*?\n\}\n\nprivate fun encodeCustomerPhoto''',
    re.S,
)

replacement = r'''private fun exportAllDebts(activity: MainActivity, debts: List<Debt>) {
    if (debts.isEmpty()) {
        Toast.makeText(activity, "ماكو ديون حتى تتصدر", Toast.LENGTH_SHORT).show()
        return
    }

    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
        Toast.makeText(activity, "تصدير كل الزبائن مدعوم على Android 10 فما فوق", Toast.LENGTH_SHORT).show()
        return
    }

    val resolver = activity.contentResolver
    val batchStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val sharedUris = ArrayList<Uri>()
    var failedCount = 0

    debts.forEachIndexed { index, debt ->
        val bitmap = try {
            makeDebtBitmap(debt)
        } catch (_: Exception) {
            failedCount++
            null
        }

        if (bitmap == null) return@forEachIndexed

        var createdUri: Uri? = null
        try {
            val fileName = String.format(Locale.US, "customer_%03d_%d.png", index + 1, debt.id)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/DaftarDeyoon/ExportAll/$batchStamp"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            createdUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create export image")

            val written = resolver.openOutputStream(createdUri!!)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: false

            if (!written) throw IllegalStateException("Unable to write export image")

            resolver.update(
                createdUri!!,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )

            sharedUris.add(createdUri!!)
        } catch (_: Exception) {
            failedCount++
            createdUri?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
        } finally {
            runCatching { bitmap.recycle() }
        }
    }

    if (sharedUris.isEmpty()) {
        Toast.makeText(activity, "تعذر تصدير بيانات الزبائن", Toast.LENGTH_SHORT).show()
        return
    }

    val intent = if (sharedUris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, sharedUris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, sharedUris)
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_SUBJECT, "تصدير دفتر ديون الكامل")
        putExtra(Intent.EXTRA_TEXT, "كشف كامل للزبائن مع جميع عمليات الدين والتسديد")
        clipData = android.content.ClipData.newUri(
            activity.contentResolver,
            "دفتر ديون",
            sharedUris.first()
        ).apply {
            for (i in 1 until sharedUris.size) {
                addItem(android.content.ClipData.Item(sharedUris[i]))
            }
        }
    }

    if (failedCount > 0) {
        Toast.makeText(
            activity,
            "تم تصدير ${sharedUris.size} زبون وتعذر تصدير $failedCount",
            Toast.LENGTH_LONG
        ).show()
    } else {
        Toast.makeText(
            activity,
            "تم تجهيز ${sharedUris.size} كشف كامل للزبائن",
            Toast.LENGTH_SHORT
        ).show()
    }

    activity.startActivity(Intent.createChooser(intent, "مشاركة دفتر الديون الكامل"))
}

private fun encodeCustomerPhoto'''

text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"exportAllDebts replacement failed: {count}")

main_path.write_text(text, encoding="utf-8")

gradle_path = Path("app/build.gradle.kts")
gradle = gradle_path.read_text(encoding="utf-8")
gradle, vc = re.subn(r'versionCode\s*=\s*\d+', 'versionCode = 3', gradle, count=1)
gradle, vn = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "2.1"', gradle, count=1)
if vc != 1 or vn != 1:
    raise SystemExit("version bump failed")
gradle_path.write_text(gradle, encoding="utf-8")

print("Export-all fix applied: one complete image per customer, all transactions included")
