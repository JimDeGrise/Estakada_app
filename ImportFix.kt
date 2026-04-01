import androidx.room.withTransaction

// ...
room.withTransaction {
    room.floorsDao().upsertAll(result.floors)
    result.owners.forEach { room.ownersDao().upsert(it) }
    room.unitsDao().upsertAll(result.units)
}