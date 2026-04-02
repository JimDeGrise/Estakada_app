package com.example.estakada

import android.content.Context
import androidx.room.Room
import com.example.estakada.data.local.AppDb
import com.example.estakada.data.local.MIGRATION_1_2

class AppGraph(context: Context) {
    val db: AppDb = Room.databaseBuilder(context, AppDb::class.java, "estakada.db")
        .addMigrations(MIGRATION_1_2)
        .build()
}