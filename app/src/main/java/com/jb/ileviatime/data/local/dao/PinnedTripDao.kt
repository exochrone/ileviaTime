package com.jb.ileviatime.data.local.dao

import androidx.room.*
import com.jb.ileviatime.data.local.entities.PinnedTripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedTripDao {
    @Query("SELECT * FROM pinned_trip ORDER BY pinned_at DESC")
    fun getAllPinnedTrips(): Flow<List<PinnedTripEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedTrip(pinnedTrip: PinnedTripEntity)

    @Delete
    suspend fun deletePinnedTrip(pinnedTrip: PinnedTripEntity)

    @Query("SELECT * FROM pinned_trip WHERE id = :id")
    suspend fun getPinnedTripById(id: Int): PinnedTripEntity?
}
