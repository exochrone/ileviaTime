package com.jb.ileviatime.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stop")
data class StopEntity(
    @PrimaryKey
    @ColumnInfo(name = "stop_id")
    val stopId: String,
    @ColumnInfo(name = "stop_name")
    val stopName: String
)
