package com.jb.ileviatime.data.local.dao

import androidx.room.*
import com.jb.ileviatime.data.local.entities.*

@Dao
interface GtfsStaticDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<StopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStopTimes(stopTimes: List<StopTimeEntity>)

    @Query("DELETE FROM route")
    suspend fun clearRoutes()

    @Query("DELETE FROM trip")
    suspend fun clearTrips()

    @Query("DELETE FROM stop")
    suspend fun clearStops()

    @Query("DELETE FROM stop_time")
    suspend fun clearStopTimes()

    @Transaction
    suspend fun clearAll() {
        clearStopTimes()
        clearTrips()
        clearRoutes()
        clearStops()
    }

    @Transaction
    suspend fun clearAllAndInsert(
        routes: List<RouteEntity>,
        trips: List<TripEntity>,
        stops: List<StopEntity>,
        stopTimes: List<StopTimeEntity>
    ) {
        clearStopTimes()
        clearTrips()
        clearRoutes()
        clearStops()
        
        insertRoutes(routes)
        insertTrips(trips)
        insertStops(stops)
        insertStopTimes(stopTimes)
    }

    @Query("SELECT * FROM route WHERE route_type = :routeType")
    suspend fun getRoutesByType(routeType: Int): List<RouteEntity>

    @Query("""
        SELECT DISTINCT s.* FROM stop s
        JOIN stop_time st ON s.stop_id = st.stop_id
        JOIN trip t ON st.trip_id = t.trip_id
        WHERE t.route_id = :routeId
        ORDER BY s.stop_name ASC
    """)
    suspend fun getStopsForRoute(routeId: String): List<StopEntity>

    @Query("""
        SELECT s.* FROM stop s
        JOIN stop_time st ON s.stop_id = st.stop_id
        WHERE st.trip_id = :tripId
        ORDER BY st.stop_sequence ASC
    """)
    suspend fun getStopsForTrip(tripId: String): List<StopEntity>
    
    @Query("SELECT * FROM trip WHERE route_id = :routeId")
    suspend fun getTripsForRoute(routeId: String): List<TripEntity>

    @Query("""
        SELECT st1.trip_id as tripId, st1.departure_time as departureTime, st2.arrival_time as arrivalTime 
        FROM stop_time st1
        JOIN stop_time st2 ON st1.trip_id = st2.trip_id
        WHERE st1.stop_id = :depStopId 
        AND st2.stop_id = :arrStopId
        AND st1.stop_sequence < st2.stop_sequence
        AND st1.trip_id IN (SELECT trip_id FROM trip WHERE route_id = :routeId)
    """)
    suspend fun getCandidateTrips(routeId: String, depStopId: String, arrStopId: String): List<TripCandidate>
}

data class TripCandidate(
    val tripId: String,
    val departureTime: String,
    val arrivalTime: String
)
