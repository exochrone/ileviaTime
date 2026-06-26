package com.jb.ileviatime.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "route")
data class RouteEntity(
    @PrimaryKey
    @ColumnInfo(name = "route_id")
    val routeId: String,
    @ColumnInfo(name = "route_short_name")
    val routeShortName: String,
    @ColumnInfo(name = "route_type")
    val routeType: Int // 3 = Bus, 0 = Tram
)
