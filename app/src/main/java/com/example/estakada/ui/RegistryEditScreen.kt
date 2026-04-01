package com.example.estakada.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.estakada.data.local.AppDb
import com.example.estakada.data.local.RegistryRowEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private fun parseShare(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistryEditScreen(
    context: Context,
    db: AppDb,
    stableId: String?, // null -> create
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var original by remember { mutableStateOf<RegistryRowEntity?>(null) }

    // поля формы
    var floor by remember { mutableStateOf("") }
    var objectNumber by remember { mutableStateOf("") }
    var sizesRaw by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var shareText by remember { mutableStateOf("") }
    var passport by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var status by remember { mutableStateOf<String?>(null) }

    // удаление (защита вводом "DEL")
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    val deleteWord = "DEL"
    val canDelete = deleteConfirmText.trim().equals(deleteWord, ignoreCase = true)

    LaunchedEffect(stableId) {
        loading = true
        status = null

        val entity = withContext(Dispatchers.IO) {
            stableId?.let { db.registryDao().getById(it) }
        }
        original = entity

        if (entity != null) {
            floor = entity.floor.orEmpty()
            objectNumber = entity.objectNumber.orEmpty()
            sizesRaw = entity.sizesRaw.orEmpty()
            ownerName = entity.ownerName.orEmpty()
            shareText = entity.share?.toString()?.replace('.', ',') ?: ""
            passport = entity.passport.orEmpty()
            plate = entity.plate.orEmpty()
            phone = entity.phone.orEmpty()
            note = entity.note.orEmpty()
        }

        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize().padding(16.dp)) { Text("Загрузка...") }
        return
    }

    val isEdit = original != null
    val saveEnabled = ownerName.trim().isNotEmpty()
    val scrollState = rememberScrollState()

    val buttonMod = Modifier.defaultMinSize(minHeight = 48.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (isEdit) "Редактирование записи" else "Добавление записи",
            style = MaterialTheme.typography.titleLarge
        )

        if (status != null) Text(status!!, style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = ownerName,
            onValueChange = { ownerName = it },
            label = { Text("ФИО *") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(value = floor, onValueChange = { floor = it }, label = { Text("Этаж") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = objectNumber, onValueChange = { objectNumber = it }, label = { Text("Помещение") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = sizesRaw, onValueChange = { sizesRaw = it }, label = { Text("Площадь (например 29,9)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = shareText,
            onValueChange = { shareText = it },
            label = { Text("Доля (например 1 или 0,5)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Телефон") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = passport, onValueChange = { passport = it }, label = { Text("Паспорт") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = plate, onValueChange = { plate = it }, label = { Text("Plate") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Примечание") }, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                modifier = buttonMod.weight(1f),
                enabled = saveEnabled,
                onClick = {
                    scope.launch {
                        status = "Сохранение..."
                        try {
                            val now = System.currentTimeMillis()
                            val id = stableId ?: UUID.randomUUID().toString()

                            val entity = RegistryRowEntity(
                                stableId = id,
                                sourceRowId = original?.sourceRowId,
                                floor = floor.trim().ifEmpty { null },
                                objectNumber = objectNumber.trim().ifEmpty { null },
                                sizesRaw = sizesRaw.trim().ifEmpty { null },
                                ownerName = ownerName.trim().ifEmpty { null },
                                share = shareText.trim().let { t -> if (t.isBlank()) null else parseShare(t) },
                                passport = passport.trim().ifEmpty { null },
                                plate = plate.trim().ifEmpty { null },
                                phone = phone.trim().ifEmpty { null },
                                note = note.trim().ifEmpty { null },
                                updatedAt = now
                            )

                            withContext(Dispatchers.IO) { db.registryDao().upsert(entity) }
                            status = "Сохранено"
                            onDone()
                        } catch (t: Throwable) {
                            status = "Ошибка: ${t.message}"
                        }
                    }
                }
            ) { Text("Сохранить") }

            OutlinedButton(
                modifier = buttonMod.weight(1f),
                onClick = onDone
            ) { Text("Отмена") }
        }

        if (isEdit) {
            val danger = MaterialTheme.colorScheme.error
            OutlinedButton(
                modifier = buttonMod.fillMaxWidth(),
                border = ButtonDefaults.outlinedButtonBorder,
                onClick = {
                    deleteConfirmText = ""
                    showDeleteDialog = true
                }
            ) {
                Text("Удалить", color = danger)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить запись?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Действие нельзя отменить.")
                    Text(
                        "Для подтверждения введите: $deleteWord",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        label = { Text("Введите $deleteWord") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canDelete,
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            status = "Удаление..."
                            try {
                                val id = stableId ?: return@launch
                                withContext(Dispatchers.IO) { db.registryDao().deleteById(id) }
                                status = "Удалено"
                                onDone()
                            } catch (t: Throwable) {
                                status = "Ошибка: ${t.message}"
                            }
                        }
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
            }
        )
    }
}