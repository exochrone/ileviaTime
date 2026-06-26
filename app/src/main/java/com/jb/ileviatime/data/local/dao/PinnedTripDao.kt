package com.jb.ileviatime.data.local.dao

import androidx.room.*
import com.jb.ileviatime.data.local.entities.PinnedTripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedTripDao {
    @Query("""
        SELECT pt.*, s1.stop_name as departure_stop_name, s2.stop_name as arrival_stop_name
        FROM pinned_trip pt
        LEFT JOIN stop s1 ON pt.departure_stop_id = s1.stop_id
        LEFT JOIN stop s2 ON pt.arrival_stop_id = s2.stop_id
        ORDER BY pt.pinned_at DESC
    """)
    fun getAllPinnedTripsWithNames(): Flow<List<PinnedTripWithNames>>

    @Query("SELECT * FROM pinned_trip ORDER BY pinned_at DESC")
    fun getAllPinnedTrips(): Flow<List<PinnedTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedTrip(pinnedTrip: PinnedTripEntity)

    @Delete
    suspend fun deletePinnedTrip(pinnedTrip: PinnedTripEntity)

    @Query("SELECT * FROM pinned_trip WHERE id = :id")
    suspend fun getPinnedTripById(id: Int): PinnedTripEntity?
}

data class PinnedTripWithNames(
    @Embedded val pinnedTrip: PinnedTripEntity,
    @ColumnInfo(name = "departure_stop_name") val departureStopName: String?,
    @ColumnInfo(name = "arrival_stop_name") val arrivalStopName: String?
)
