package com.example.estakada.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.estakada.AppGraph
import com.example.estakada.data.local.RegistryRowEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Locale

private fun readTextUtf8(context: Context, uri: Uri): String {
    context.contentResolver.openInputStream(uri)!!.use { input ->
        BufferedReader(InputStreamReader(input, Charset.forName("UTF-8"))).use { br ->
            return br.readText()
        }
    }
}

private fun detectDelimiter(text: String): Char {
    val sample = text.lineSequence().take(5).joinToString("\n")
    val semis = sample.count { it == ';' }
    val commas = sample.count { it == ',' }
    return if (semis >= commas) ';' else ','
}

private fun splitCsvLine(line: String, delimiter: Char): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' -> {
                // double quote inside quoted field -> escaped quote
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i += 1
                } else {
                    inQuotes = !inQuotes
                }
            }
            c == delimiter && !inQuotes -> {
                out.add(sb.toString().trim())
                sb.setLength(0)
            }
            else -> sb.append(c)
        }
        i += 1
    }
    out.add(sb.toString().trim())
    return out
}

private data class CsvPreview(
    val header: List<String>,
    val sampleRows: List<List<String>>,
    val rowsCount: Int
)

private fun previewParse(text: String, delimiter: Char, sampleSize: Int = 3): CsvPreview {
    val lines = text.lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()

    if (lines.isEmpty()) return CsvPreview(emptyList(), emptyList(), 0)

    val header = splitCsvLine(lines.first(), delimiter)
    val dataLines = lines.drop(1)
    val sampleRows = dataLines.take(sampleSize).map { splitCsvLine(it, delimiter) }
    return CsvPreview(header, sampleRows, dataLines.size)
}

private fun normalizeKeyPart(s: String?): String {
    return (s ?: "")
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}

private fun sha256Hex(text: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
    return buildString(bytes.size * 2) {
        for (b in bytes) append("%02x".format(b))
    }
}

private fun toDoubleOrNullSmart(raw: String?): Double? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    // "10,2" -> 10.2
    return s.replace(',', '.').toDoubleOrNull()
}

private fun toIntOrNullSmart(raw: String?): Int? =
    raw?.trim()?.toIntOrNull()

private fun mapRowsToEntities(
    header: List<String>,
    rows: List<List<String>>,
    now: Long
): List<RegistryRowEntity> {
    val index = header
        .mapIndexed { i, name -> name.trim().lowercase(Locale.ROOT) to i }
        .toMap()

    fun get(row: List<String>, col: String): String? {
        val i = index[col] ?: return null
        return row.getOrNull(i)?.trim()?.ifEmpty { null }
    }

    return rows.map { row ->
        val floor = get(row, "floor")
        val objectNumber = get(row, "object_number")
        val plate = get(row, "plate")
        val passport = get(row, "passport")
        val name = get(row, "name")
        val phone = get(row, "phone")

        // gender в CSV = доля собственности
        val share = toDoubleOrNullSmart(get(row, "gender"))

        // стабильный ключ: floor+object + (plate если есть, иначе passport+name+phone)
        val key = buildString {
            append(normalizeKeyPart(floor))
            append("|")
            append(normalizeKeyPart(objectNumber))
            append("|")
            if (!plate.isNullOrBlank()) {
                append("plate:")
                append(normalizeKeyPart(plate))
            } else {
                append("person:")
                append(normalizeKeyPart(passport))
                append("|")
                append(normalizeKeyPart(name))
                append("|")
                append(normalizeKeyPart(phone))
            }
        }

        RegistryRowEntity(
            stableId = sha256Hex(key),
            sourceRowId = toIntOrNullSmart(get(row, "id")),
            floor = floor,
            objectNumber = objectNumber,
            sizesRaw = get(row, "sizes"),
            ownerName = name,
            share = share,
            passport = passport,
            plate = plate,
            phone = phone,
            note = get(row, "note"),
            updatedAt = now
        )
    }
}

@Composable
fun CsvPreviewScreen(
    context: Context,
    onImported: () -> Unit
) {
    val graph = remember { AppGraph(context.applicationContext) }

    var status by remember { mutableStateOf("Выберите CSV файл") }
    var delimiter by remember { mutableStateOf<Char?>(null) }

    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var lastCsvText by remember { mutableStateOf<String?>(null) }

    var importStatus by remember { mutableStateOf<String?>(null) }
    var canImport by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Чтение файла..."
            importStatus = null
            canImport = false
            try {
                val text = withContext(Dispatchers.IO) { readTextUtf8(context, uri) }
                val d = detectDelimiter(text)
                val p = previewParse(text, d, sampleSize = 3)

                delimiter = d
                preview = p
                lastCsvText = text
                status = "ОК"
                canImport = p.header.isNotEmpty() && p.rowsCount > 0
            } catch (t: Throwable) {
                status = "Ошибка: ${t.message}"
                delimiter = null
                preview = null
                lastCsvText = null
            }
        }
    }

    fun parseAllRows(text: String, d: Char): Pair<List<String>, List<List<String>>> {
        val lines = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return emptyList<String>() to emptyList()

        val header = splitCsvLine(lines.first(), d)
        val rows = lines.drop(1).map { splitCsvLine(it, d) }
        return header to rows
    }

    Column(Modifier.padding(16.dp)) {
        Text("CSV предпросмотр", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row {
            Button(onClick = { pick.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) }) {
                Text("Выбрать CSV")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                enabled = canImport && lastCsvText != null && delimiter != null,
                onClick = {
                    val text = lastCsvText ?: return@Button
                    val d = delimiter ?: return@Button
                    scope.launch {
                        importStatus = "Импорт в базу..."
                        try {
                            val now = System.currentTimeMillis()

                            val (header, rows) = withContext(Dispatchers.Default) {
                                parseAllRows(text, d)
                            }

                            val entities = withContext(Dispatchers.Default) {
                                mapRowsToEntities(header, rows, now)
                            }

                            withContext(Dispatchers.IO) {
                                graph.db.registryDao().upsertAll(entities)
                            }

                            importStatus = "Готово. Записей импортировано/обновлено: ${entities.size}"
                            onImported()
                        } catch (t: Throwable) {
                            importStatus = "Ошибка импорта: ${t.message}"
                        }
                    }
                }
            ) {
                Text("Импортировать в базу (Room)")
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Статус: $status")
        Text("Разделитель: ${delimiter ?: '—'}")

        val p = preview
        Text("Строк данных: ${p?.rowsCount ?: 0}")

        Spacer(Modifier.height(12.dp))
        Text("Заголовки:")
        if (p == null || p.header.isEmpty()) {
            Text("—")
        } else {
            p.header.forEach { Text("• $it") }
        }

        Spacer(Modifier.height(12.dp))
        Text("Пример строк:")
        if (p == null || p.sampleRows.isEmpty()) {
            Text("—")
        } else {
            p.sampleRows.forEachIndexed { idx, row ->
                Text("#${idx + 1}: ${row.joinToString(" | ")}")
            }
        }

        Spacer(Modifier.height(12.dp))
        if (importStatus != null) {
            Text("Импорт: $importStatus")
        }
    }
}