from pathlib import Path

path = Path("app/src/main/java/com/blusher/cosmetics/MainActivity.kt")
text = path.read_text(encoding="utf-8")

old_launcher = '''    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val imported = importDebtsFromUri(activity, uri)
            if (imported != null) {
                debts = imported
                activity.latestDebts = imported
                saveLocalDebts(activity, imported)
                saveBackup(activity, imported)
                Toast.makeText(activity, "تم استيراد دفتر الديون بنجاح", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "تعذر قراءة ملف النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
            }
        }
    }
'''

new_launcher = '''    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val imported = importDebtsFromUris(activity, uris)
            if (imported != null) {
                debts = imported
                activity.latestDebts = imported
                saveLocalDebts(activity, imported)
                saveBackup(activity, imported)
                val filesWord = if (uris.size == 1) "ملف" else "ملفات"
                Toast.makeText(activity, "تم استيراد ${uris.size} $filesWord واسترجاع ${imported.size} زبون", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(activity, "ما لكينا نسخة احتياطية صالحة ضمن الملفات المحددة", Toast.LENGTH_LONG).show()
            }
        }
    }
'''

if old_launcher not in text:
    raise SystemExit("Import launcher block not found; refusing to patch an unexpected source file")
text = text.replace(old_launcher, new_launcher, 1)

marker = '''private fun importDebtsFromUri(activity: MainActivity, uri: Uri): List<Debt>? {'''
helper = '''private fun importDebtsFromUris(activity: MainActivity, uris: List<Uri>): List<Debt>? {
    if (uris.isEmpty()) return null

    val candidates = uris.mapNotNull { uri ->
        importDebtsFromUri(activity, uri)?.takeIf { it.isNotEmpty() }
    }
    if (candidates.isEmpty()) return null

    fun debtScore(debt: Debt): Long {
        val operationCount = maxOf(orderedTransactions(debt).size, debt.payments.size + debt.additions.size)
        var score = operationCount.toLong() * 1_000_000L
        score += debt.payments.size.toLong() * 10_000L
        score += debt.additions.size.toLong() * 10_000L
        if (debt.photoData.isNotBlank()) score += 1_000L
        if (debt.phone.isNotBlank()) score += 100L
        if (debt.location.isNotBlank()) score += 50L
        if (debt.note.isNotBlank()) score += 25L
        score += debt.paidAmount.coerceAtLeast(0.0).toLong().coerceAtMost(10_000L)
        return score
    }

    // Start from the richest/most recent-looking snapshots, then recover missing
    // customers from the rest. Duplicate customer IDs are kept only once.
    val sortedCandidates = candidates.sortedByDescending { list ->
        list.sumOf { debtScore(it) } + list.size.toLong() * 100_000L
    }

    val merged = LinkedHashMap<Long, Debt>()
    sortedCandidates.forEach { list ->
        list.forEach { debt ->
            val existing = merged[debt.id]
            if (existing == null || debtScore(debt) > debtScore(existing)) {
                merged[debt.id] = debt
            }
        }
    }

    return merged.values.sortedBy { it.id }
}

'''

if marker not in text:
    raise SystemExit("Import helper marker not found; refusing to patch an unexpected source file")
text = text.replace(marker, helper + marker, 1)

path.write_text(text, encoding="utf-8")
print("Enabled multi-file backup import with safe recovery/merge")
