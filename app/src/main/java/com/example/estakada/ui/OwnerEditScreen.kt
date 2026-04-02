package com.example.estakada.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.estakada.data.local.AppDb
import com.example.estakada.data.local.OwnerEntity
import com.example.estakada.util.PhoneFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun OwnerEditScreen(
    context: Context,
    db: AppDb,
    ownerId: String?,   // null → create new
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var original by remember { mutableStateOf<OwnerEntity?>(null) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    var status by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        loading = true
        val entity = withContext(Dispatchers.IO) {
            ownerId?.let { db.ownersDao().getById(it) }
        }
        original = entity
        if (entity != null) {
            name = entity.name
            phone = entity.phone?.let { PhoneFormat.toDisplay(it) } ?: ""
            note = entity.note.orEmpty()
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize().padding(16.dp)) { Text("Загрузка...") }
        return
    }

    val isEdit = original != null
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
            if (isEdit) "Редактирование собственника" else "Добавление собственника",
            style = MaterialTheme.typography.titleLarge
        )

        if (status != null) {
            Text(status!!, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("ФИО *") },
            modifier = Modifier.fillMaxWidth()
        )

        Column {
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    phoneError = null
                },
                label = { Text("Телефон") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                isError = phoneError != null
            )
            if (phoneError != null) {
                Text(
                    phoneError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Примечание") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                modifier = buttonMod.weight(1f),
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    val rawPhone = phone.trim()
                    val storedPhone: String? = if (rawPhone.isEmpty()) {
                        null
                    } else {
                        val normalized = PhoneFormat.normalize(rawPhone)
                        if (normalized == null) {
                            phoneError = "Некорректный формат. Используйте +7XXXXXXXXXX"
                            return@Button
                        }
                        normalized
                    }

                    scope.launch {
                        status = "Сохранение..."
                        try {
                            val id = ownerId ?: UUID.randomUUID().toString()
                            val entity = OwnerEntity(
                                id = id,
                                name = name.trim(),
                                phone = storedPhone,
                                note = note.trim().ifEmpty { null },
                                updatedAt = System.currentTimeMillis()
                            )
                            withContext(Dispatchers.IO) { db.ownersDao().upsert(entity) }
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

        Spacer(Modifier.height(24.dp))
    }
}
