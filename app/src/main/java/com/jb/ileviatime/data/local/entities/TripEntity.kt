package com.jb.ileviatime.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["route_id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["route_id"])]
)
data class TripEntity(
    @PrimaryKey
    @ColumnInfo(name = "trip_id")
    val tripId: String,
    @ColumnInfo(name = "route_id")
    val routeId: String
)
