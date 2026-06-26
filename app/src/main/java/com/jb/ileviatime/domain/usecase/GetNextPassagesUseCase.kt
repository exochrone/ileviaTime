package com.jb.ileviatime.domain.usecase

import com.google.transit.realtime.FeedMessage
import com.google.transit.realtime.TripUpdate
import com.jb.ileviatime.data.local.dao.GtfsStaticDao
import com.jb.ileviatime.data.repository.GtfsRtRepository
import com.jb.ileviatime.domain.model.*
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject

class GetNextPassagesUseCase @Inject constructor(
    private val rtRepository: GtfsRtRepository,
    private val staticDao: GtfsStaticDao
) {
    private var lastSuccessfulFeed: FeedMessage? = null
    private var lastUpdateAt: Long = 0

    operator fun invoke(pinnedTrip: PinnedTrip): Flow<PassagesState> {
        return rtRepository.feedFlow.map { result ->
            val now = System.currentTimeMillis() / 1000
            
            val feed = result.fold(
                onSuccess = { 
                    lastSuccessfulFeed = it
                    lastUpdateAt = now
                    it 
                },
                onFailure = { lastSuccessfulFeed }
            )
            
            val candidates = staticDao.getCandidateTrips(
                pinnedTrip.routeId,
                pinnedTrip.departureStopId,
                pinnedTrip.arrivalStopId
            )
            
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
                    is PassageTime.Scheduled -> d.epochSeconds >= now - 300
                    else -> false
                }
            }

            val status = if (result.isSuccess) DataStatus.Fresh else if (lastSuccessfulFeed != null) DataStatus.Stale(lastUpdateAt) else DataStatus.Loading

            val passages = if (feed == null) {
                staticPassages.take(3)
            } else {
                val rtPassages = correlateWithRt(feed, pinnedTrip)
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

            PassagesState(passages, status)
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

    private fun extractTime(update: TripUpdate.StopTimeUpdate): PassageTime {
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
            
            // Set to current day's midnight first
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            
            // Add hours, minutes, seconds. Calendar handles hours >= 24 correctly.
            calendar.add(Calendar.HOUR_OF_DAY, parts[0])
            calendar.add(Calendar.MINUTE, parts[1])
            calendar.add(Calendar.SECOND, parts[2])

            calendar.timeInMillis / 1000
        } catch (_: Exception) {
            0L
        }
    }
}
