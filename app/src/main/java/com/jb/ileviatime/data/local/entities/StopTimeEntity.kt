package com.jb.ileviatime.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "stop_time",
    primaryKeys = ["trip_id", "stop_sequence"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["trip_id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["stop_id"],
            childColumns = ["stop_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trip_id"]),
        Index(value = ["stop_id"])
    ]
)
data class StopTimeEntity(
    @ColumnInfo(name = "trip_id")
    val tripId: String,
    @ColumnInfo(name = "stop_id")
    val stopId: String,
    @ColumnInfo(name = "stop_sequence")
    val stopSequence: Int,
    @ColumnInfo(name = "arrival_time")
    val arrivalTime: String,
    @ColumnInfo(name = "departure_time")
    val departureTime: String
)
