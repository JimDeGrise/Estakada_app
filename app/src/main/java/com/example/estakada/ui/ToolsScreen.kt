package com.example.estakada.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(
    hasData: Boolean,
    exportInProgress: Boolean,
    exportStatus: String?,
    onGoImport: () -> Unit,
    onGoRegistry: () -> Unit,
    onExport: () -> Unit,
    onClearDb: () -> Unit,
    onAdd: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf("") }

    val required = "Эстакада"
    val canConfirm = confirmText.trim().equals(required, ignoreCase = true)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        //Text("Инструменты", style = MaterialTheme.typography.titleLarge)
        //Spacer(Modifier.height(12.dp))

        if (!hasData) {
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("База пустая.")
                    Spacer(Modifier.height(6.dp))
                    Text("Сначала выполните импорт CSV, затем данные появятся в Реестре.")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onGoImport
        ) { Text("Импорт CSV") }

        Spacer(Modifier.height(10.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = hasData && !exportInProgress,
            onClick = onExport
        ) {
            if (exportInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Экспорт...")
            } else {
                Text("Экспорт CSV")
            }
        }

        if (exportStatus != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = exportStatus, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = hasData,
            onClick = onGoRegistry
        ) { Text("Открыть реестр") }

        Spacer(Modifier.height(10.dp))

        // НОВОЕ: добавление записи
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAdd
        ) { Text("Добавить запись") }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = hasData,
            onClick = {
                confirmText = ""
                showClearDialog = true
            }
        ) { Text("Очистить базу") }

        Spacer(Modifier.height(8.dp))
        Text(
            "Экспорт создаёт CSV через диалог «Сохранить как…» (обычно в Downloads).",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить базу?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Это удалит все записи из локальной базы на устройстве. Действие нельзя отменить.")
                    Text(
                        "Для подтверждения введите слово: $required",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        singleLine = true,
                        label = { Text("Введите $required") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canConfirm,
                    onClick = {
                        showClearDialog = false
                        onClearDb()
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }
}