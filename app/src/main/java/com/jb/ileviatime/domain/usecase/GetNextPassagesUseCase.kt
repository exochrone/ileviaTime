package com.jb.ileviatime.domain.usecase

import com.google.transit.realtime.FeedMessage
import com.jb.ileviatime.data.local.dao.GtfsStaticDao
import com.jb.ileviatime.data.repository.GtfsRtRepository
import com.jb.ileviatime.domain.model.PassageTime
import com.jb.ileviatime.domain.model.PinnedTrip
import com.jb.ileviatime.domain.model.TripPassage
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

class GetNextPassagesUseCase @Inject constructor(
    private val rtRepository: GtfsRtRepository,
    private val staticDao: GtfsStaticDao
) {
    operator fun invoke(pinnedTrip: PinnedTrip): Flow<List<TripPassage>> {
        return rtRepository.feedFlow.map { result ->
            val feed = result.getOrNull()
            
            val candidates = staticDao.getCandidateTrips(
                pinnedTrip.routeId,
                pinnedTrip.departureStopId,
                pinnedTrip.arrivalStopId
            )
            
            val now = System.currentTimeMillis() / 1000

            val staticPassages = candidates.map { candidate ->
                val depEpoch = convertGtfsTimeToEpoch(candidate.departureTime)
                val arrEpoch = convertGtfsTimeToEpoch(candidate.arrivalTime)
                TripPassage(
                    tripId = candidate.tripId,
                    departure = PassageTime.Scheduled(depEpoch),
                    arrival = PassageTime.Scheduled(arrEpoch)
                )
            }.filter { 
                when (val d = it.departure) {
                    is PassageTime.Scheduled -> d.epochSeconds >= now - 300 // Keep trips from last 5 mins
                    else -> false
                }
            }

            if (feed == null) {
                return@map staticPassages.take(3)
            }

            // Correlate with RT
            val rtPassages = correlateWithRt(feed, pinnedTrip)
            
            // Merge RT and Static: Prefer RT for same tripId
            val rtMap = rtPassages.associateBy { it.tripId }
            val merged = staticPassages.map { static ->
                rtMap[static.tripId] ?: static
            }

            merged.sortedBy { 
                when (val d = it.departure) {
                    is PassageTime.RealTime -> d.epochSeconds
                    is PassageTime.Scheduled -> d.epochSeconds
                    else -> Long.MAX_VALUE
                }
            }.take(3)
        }
    }

    private fun correlateWithRt(
        feed: FeedMessage,
        pinnedTrip: PinnedTrip
    ): List<TripPassage> {
        return feed.entityList
            .filter { it.hasTripUpdate() && it.tripUpdate.trip.routeId == pinnedTrip.routeId }
            .mapNotNull { entity ->
                val update = entity.tripUpdate
                val departureUpdate = update.stopTimeUpdateList.find { it.stopId == pinnedTrip.departureStopId }
                val arrivalUpdate = update.stopTimeUpdateList.find { it.stopId == pinnedTrip.arrivalStopId }
                
                if (departureUpdate != null && arrivalUpdate != null) {
                    TripPassage(
                        tripId = update.trip.tripId,
                        departure = extractTime(departureUpdate),
                        arrival = extractTime(arrivalUpdate)
                    )
                } else {
                    null
                }
            }
    }

    private fun extractTime(update: com.google.transit.realtime.TripUpdate.StopTimeUpdate): PassageTime {
        val time = if (update.hasDeparture()) {
            update.departure.time
        } else if (update.hasArrival()) {
            update.arrival.time
        } else {
            0L
        }
        return if (time > 0) PassageTime.RealTime(time) else PassageTime.NotAvailable
    }

    private fun convertGtfsTimeToEpoch(gtfsTime: String): Long {
        return try {
            val parts = gtfsTime.split(":").map { it.trim().toInt() }
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, parts[0])
            calendar.set(Calendar.MINUTE, parts[1])
            calendar.set(Calendar.SECOND, parts[2])
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis / 1000
        } catch (e: Exception) {
            0L
        }
    }
}
