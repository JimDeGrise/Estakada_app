package com.example.estakada.data.local

import androidx.room.Dao
import androidx.room.Upsert

@Dao
interface FloorsDao {
    @Upsert suspend fun upsertAll(items: List<FloorEntity>)
}

@Dao
interface OwnersDao {
    @Upsert suspend fun upsertAll(items: List<OwnerEntity>)
}

@Dao
interface UnitsDao {
    @Upsert suspend fun upsertAll(items: List<UnitEntity>)
}