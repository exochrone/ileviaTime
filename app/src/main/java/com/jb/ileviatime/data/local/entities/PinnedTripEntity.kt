package com.jb.ileviatime.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_trip")
data class PinnedTripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "route_id")
    val routeId: String,
    @ColumnInfo(name = "departure_stop_id")
    val departureStopId: String,
    @ColumnInfo(name = "arrival_stop_id")
    val arrivalStopId: String,
    @ColumnInfo(name = "transport_mode")
    val transportMode: String, // "BUS" | "TRAM"
    @ColumnInfo(name = "pinned_at")
    val pinnedAt: Long
)
