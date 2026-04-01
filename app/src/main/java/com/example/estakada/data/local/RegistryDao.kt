package com.example.estakada.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RegistryDao {
    @Upsert
    suspend fun upsertAll(items: List<RegistryRowEntity>)

    @Query("SELECT * FROM registry_rows ORDER BY floor, objectNumber")
    fun observeAll(): Flow<List<RegistryRowEntity>>

    @Query("SELECT * FROM registry_rows ORDER BY floor, objectNumber")
    suspend fun getAll(): List<RegistryRowEntity>

    @Query("SELECT COUNT(*) FROM registry_rows")
    suspend fun count(): Int

    @Query("DELETE FROM registry_rows")
    suspend fun clearAll()

    @Query("SELECT * FROM registry_rows WHERE stableId = :id LIMIT 1")
    suspend fun getById(id: String): RegistryRowEntity?

    @Upsert
    suspend fun upsert(item: RegistryRowEntity)

    @Query("DELETE FROM registry_rows WHERE stableId = :id")
    suspend fun deleteById(id: String)
}