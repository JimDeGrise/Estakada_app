package com.example.estakada.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "floors")
data class FloorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val order: Int,
    val updatedAt: Long
)

@Entity(tableName = "owners")
data class OwnerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val note: String?,
    val updatedAt: Long
)

enum class UnitStatus { FREE, OCCUPIED, DEBT }

@Entity(tableName = "units")
data class UnitEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val number: String,
    val areaM2: Double,
    val ownershipShare: Double?,
    val ownerId: String?,
    val status: UnitStatus,
    val note: String?,
    val updatedAt: Long
)