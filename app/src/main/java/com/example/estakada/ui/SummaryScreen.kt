package com.example.estakada.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.estakada.data.local.RegistryRowEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale

private fun parseAreaSummary(raw: String?): Double? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    return s.replace(',', '.').toDoubleOrNull()
}

private fun fmtArea(v: Double): String =
    String.format(Locale.ROOT, "%.1f", v).replace('.', ',')

private data class SummaryData(
    val ownerCount: Int,
    val totalArea: Double,
    val invalidAreaCount: Int,
    val areaByFloor: List<Pair<String, Double>>
)

private fun computeSummary(rows: List<RegistryRowEntity>): SummaryData {
    val ownerCount = rows
        .map { it.ownerName?.trim().orEmpty() }
        .filter { it.isNotBlank() }
        .distinct()
        .size

    val totalArea = rows.sumOf { r ->
        parseAreaSummary(r.sizesRaw)?.takeIf { it > 0 } ?: 0.0
    }

    val invalidAreaCount = rows.count { r ->
        val a = parseAreaSummary(r.sizesRaw)
        a == null || a <= 0
    }

    val areaByFloor = rows
        .groupBy { it.floor?.trim().orEmpty().ifBlank { "—" } }
        .map { (floor, floorRows) ->
            floor to floorRows.sumOf { r ->
                parseAreaSummary(r.sizesRaw)?.takeIf { it > 0 } ?: 0.0
            }
        }
        .sortedWith(compareBy(
            { it.first.toIntOrNull() ?: Int.MAX_VALUE },
            { it.first }
        ))

    return SummaryData(ownerCount, totalArea, invalidAreaCount, areaByFloor)
}

@Composable
fun SummaryScreen(
    rowsFlow: Flow<List<RegistryRowEntity>>,
    onClose: () -> Unit
) {
    val rows by rowsFlow.collectAsState(initial = emptyList())
    val summary = remember(rows) { computeSummary(rows) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Сводные данные", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
            }
        }

        Spacer(Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Число собственников: ${summary.ownerCount}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Общая занимаемая площадь: ${fmtArea(summary.totalArea)} кв.м.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "Некорректная площадь: ${summary.invalidAreaCount} записей",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text("Площадь по этажам", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (summary.areaByFloor.isEmpty()) {
                    Text("Нет данных", style = MaterialTheme.typography.bodyMedium)
                } else {
                    summary.areaByFloor.forEach { (floor, area) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Этаж $floor", style = MaterialTheme.typography.bodyMedium)
                            Text("${fmtArea(area)} кв.м.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
