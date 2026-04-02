package com.example.estakada.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `owners` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `phone` TEXT,
                `note` TEXT,
                `updatedAt` INTEGER NOT NULL
            )"""
        )
    }
}

@Database(
    entities = [RegistryRowEntity::class, OwnerEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun registryDao(): RegistryDao
    abstract fun ownersDao(): OwnersDao
}