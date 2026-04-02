package com.example.estakada.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorsDao {
    @Upsert suspend fun upsertAll(items: List<FloorEntity>)
}

@Dao
interface OwnersDao {
    @Upsert suspend fun upsertAll(items: List<OwnerEntity>)
    @Upsert suspend fun upsert(item: OwnerEntity)
    @Query("SELECT * FROM owners WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OwnerEntity?
    @Query("DELETE FROM owners WHERE id = :id")
    suspend fun deleteById(id: String)
    @Query("SELECT * FROM owners ORDER BY name")
    fun observeAll(): Flow<List<OwnerEntity>>
}

@Dao
interface UnitsDao {
    @Upsert suspend fun upsertAll(items: List<UnitEntity>)
}