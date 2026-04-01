package com.example.estakada.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.estakada.data.local.AppDb
import com.example.estakada.data.local.RegistryRowEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun parseArea(raw: String?): Double? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    return s.replace(',', '.').toDoubleOrNull()
}

private fun fmt1(v: Double?): String {
    if (v == null) return "—"
    return String.format(Locale.ROOT, "%.1f", v).replace('.', ',')
}

private fun fmt3(v: Double?): String {
    if (v == null) return "—"
    return String.format(Locale.ROOT, "%.3f", v)
        .trimEnd('0')
        .trimEnd('.')
        .replace('.', ',')
}

private fun normalizeOwnerName(s: String?): String = s?.trim().orEmpty()

private fun dialPhone(context: Context, raw: String?) {
    val phone = raw?.trim().orEmpty()
    if (phone.isBlank()) return

    // Нормализуем: оставим цифры и +
    val normalized = phone.replace(Regex("[^0-9+]"), "")
    if (normalized.isBlank()) return

    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    context.startActivity(intent)
}

private data class OwnerCardData(
    val ownerName: String,
    val rows: List<RegistryRowEntity>,
    val totalArea: Double?,
    val totalShare: Double?,
    val missingShareCount: Int
) {
    val passports: List<String> = rows.mapNotNull { it.passport?.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val phones: List<String> = rows.mapNotNull { it.phone?.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val plates: List<String> = rows.mapNotNull { it.plate?.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}

@Composable
fun RegistryListScreen(
    context: Context,
    db: AppDb,
    rowsFlow: Flow<List<RegistryRowEntity>>,
    onEdit: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Диалог "одна запись" (старый режим)
    var selectedRow by remember { mutableStateOf<RegistryRowEntity?>(null) }

    // Диалог "все объекты собственника"
    var ownerDialog by remember { mutableStateOf<OwnerCardData?>(null) }

    val scope = rememberCoroutineScope()

    val filteredFlow = remember(rowsFlow, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) rowsFlow
        else rowsFlow.map { rows ->
            rows.filter { r ->
                (r.floor ?: "").lowercase().contains(q) ||
                    (r.objectNumber ?: "").lowercase().contains(q) ||
                    (r.ownerName ?: "").lowercase().contains(q) ||
                    (r.phone ?: "").lowercase().contains(q) ||
                    (r.passport ?: "").lowercase().contains(q) ||
                    (r.plate ?: "").lowercase().contains(q)
            }
        }
    }

    val rows by filteredFlow.collectAsState(initial = emptyList())

    // Счётчик объектов по собственнику в рамках текущего списка (учитывает поиск/фильтры)
    val ownerCounts = remember(rows) {
        rows.asSequence()
            .map { normalizeOwnerName(it.ownerName) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск (ФИО / телефон / помещение / паспорт / plate)") },
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))
        Text("Записей: ${rows.size}")

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rows, key = { it.stableId }) { row ->
                val owner = normalizeOwnerName(row.ownerName)
                val hasMultiple = owner.isNotBlank() && (ownerCounts[owner] ?: 0) > 1

                RegistryRowCard(
                    row = row,
                    showOwnerButton = hasMultiple,
                    onClickRow = { selectedRow = row }, // старый режим
                    onClickOwner = {
                        if (owner.isBlank()) return@RegistryRowCard

                        scope.launch {
                            val allOwnerRows = withContext(Dispatchers.Default) {
                                rows.filter { normalizeOwnerName(it.ownerName) == owner }
                                    .sortedWith(compareBy<RegistryRowEntity>(
                                        { it.floor ?: "" },
                                        { it.objectNumber ?: "" }
                                    ))
                            }

                            val totalArea = withContext(Dispatchers.Default) {
                                val areas = allOwnerRows.mapNotNull { parseArea(it.sizesRaw) }
                                if (areas.isEmpty()) null else areas.sum()
                            }

                            val missingShareCount = withContext(Dispatchers.Default) {
                                allOwnerRows.count { it.share == null }
                            }

                            val totalShare = withContext(Dispatchers.Default) {
                                val shares = allOwnerRows.mapNotNull { it.share }
                                if (shares.isEmpty()) null else shares.sum()
                            }

                            ownerDialog = OwnerCardData(
                                ownerName = owner,
                                rows = allOwnerRows,
                                totalArea = totalArea,
                                totalShare = totalShare,
                                missingShareCount = missingShareCount
                            )
                        }
                    }
                )
            }
        }
    }

    // Диалог одной записи (старый режим)
    if (selectedRow != null) {
        val r = selectedRow!!
        AlertDialog(
            onDismissRequest = { selectedRow = null },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            selectedRow = null
                            onEdit(r.stableId)
                        }
                    ) { Text("Редактировать") }

                    TextButton(onClick = { selectedRow = null }) { Text("Закрыть") }
                }
            },
            title = { Text("Карточка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Этаж: ${r.floor ?: "—"}")
                    Text("Помещение: ${r.objectNumber ?: "—"}")
                    Text("Площадь: ${r.sizesRaw ?: "—"}")
                    Text("Доля: ${r.share?.let { fmt3(it) } ?: "—"}")
                    Text("ФИО: ${r.ownerName ?: "—"}")

                    val phoneText = r.phone?.trim().orEmpty()
                    if (phoneText.isNotBlank()) {
                        TextButton(
                            contentPadding = PaddingValues(0.dp),
                            onClick = { dialPhone(context, phoneText) }
                        ) { Text("Телефон: $phoneText") }
                    } else {
                        Text("Телефон: —")
                    }

                    Text("Паспорт: ${r.passport ?: "—"}")
                    Text("Plate: ${r.plate ?: "—"}")
                    Text("Примечание: ${r.note ?: "—"}")
                }
            }
        )
    }

    // Диалог собственника (все объекты) — ДОБАВИЛИ ПРОКРУТКУ
    if (ownerDialog != null) {
        val data = ownerDialog!!
        val dialogScroll = rememberScrollState()

        AlertDialog(
            onDismissRequest = { ownerDialog = null },
            confirmButton = {
                TextButton(onClick = { ownerDialog = null }) { Text("Закрыть") }
            },
            title = { Text("Все объекты") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp) // важно: ограничиваем высоту диалога
                        .verticalScroll(dialogScroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(data.ownerName, style = MaterialTheme.typography.titleMedium)

                    if (data.passports.isNotEmpty()) Text("Паспорта: ${data.passports.joinToString(", ")}")

                    if (data.phones.isNotEmpty()) {
                        Text("Телефоны:", style = MaterialTheme.typography.bodyMedium)
                        data.phones.forEach { p ->
                            TextButton(
                                contentPadding = PaddingValues(0.dp),
                                onClick = { dialPhone(context, p) }
                            ) { Text(p) }
                        }
                    }

                    if (data.plates.isNotEmpty()) Text("Plate: ${data.plates.joinToString(", ")}")

                    Divider()

                    Text("Объектов: ${data.rows.size}")
                    Text("Общая площадь: ${fmt1(data.totalArea)}")
                    Text(
                        buildString {
                            append("Общая доля: ${fmt3(data.totalShare)}")
                            if (data.missingShareCount > 0) append(" (пропусков: ${data.missingShareCount})")
                        }
                    )

                    Divider()

                    data.rows.forEach { rr ->
                        val line = buildString {
                            append("Этаж ${rr.floor ?: "—"}")
                            append(" • ")
                            append(rr.objectNumber ?: "—")
                            append(" • ")
                            append(rr.sizesRaw ?: "—")
                            if (rr.share != null) append(" • доля: ${fmt3(rr.share)}")
                        }
                        Text(line)
                    }
                }
            }
        )
    }
}

@Composable
private fun RegistryRowCard(
    row: RegistryRowEntity,
    showOwnerButton: Boolean,
    onClickRow: () -> Unit,
    onClickOwner: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClickRow() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Этаж ${row.floor ?: "—"} • ${row.objectNumber ?: "—"} • ${row.sizesRaw ?: "—"}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = row.ownerName ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val secondary = listOfNotNull(
                        row.phone?.takeIf { it.isNotBlank() }?.let { "тел: $it" },
                        row.share?.let { "доля: ${fmt3(it)}" }
                    ).joinToString(" • ")

                    if (secondary.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showOwnerButton) {
                    TextButton(onClick = onClickOwner) {
                        Text("Все объекты")
                    }
                }
            }
        }
    }
}