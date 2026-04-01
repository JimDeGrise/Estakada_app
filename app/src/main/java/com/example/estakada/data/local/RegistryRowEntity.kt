package com.example.estakada.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registry_rows")
data class RegistryRowEntity(
    @PrimaryKey val stableId: String,   // генерим сами
    val sourceRowId: Int?,              // CSV "id"
    val floor: String?,
    val objectNumber: String?,
    val sizesRaw: String?,              // как в CSV, например "10,2"
    val ownerName: String?,
    val share: Double?,                 // CSV "gender" -> доля собственности
    val passport: String?,
    val plate: String?,
    val phone: String?,
    val note: String?,
    val updatedAt: Long
)