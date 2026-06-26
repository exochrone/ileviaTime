package com.jb.ileviatime.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jb.ileviatime.data.local.dao.GtfsStaticDao
import com.jb.ileviatime.data.local.dao.PinnedTripDao
import com.jb.ileviatime.data.local.entities.*

@Database(
    entities = [
        RouteEntity::class,
        TripEntity::class,
        StopEntity::class,
        StopTimeEntity::class,
        PinnedTripEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gtfsStaticDao(): GtfsStaticDao
    abstract fun pinnedTripDao(): PinnedTripDao
}
