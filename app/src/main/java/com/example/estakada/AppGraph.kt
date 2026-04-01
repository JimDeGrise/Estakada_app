package com.example.estakada

import android.content.Context
import androidx.room.Room
import com.example.estakada.data.local.AppDb

class AppGraph(context: Context) {
    val db: AppDb = Room.databaseBuilder(context, AppDb::class.java, "estakada.db").build()
}