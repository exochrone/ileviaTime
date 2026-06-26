package com.jb.ileviatime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jb.ileviatime.data.local.dao.PinnedTripDao
import com.jb.ileviatime.data.local.entities.PinnedTripEntity
import com.jb.ileviatime.data.repository.GtfsRtRepository
import com.jb.ileviatime.domain.model.PinnedTrip
import com.jb.ileviatime.domain.usecase.GetNextPassagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pinnedTripDao: PinnedTripDao,
    private val getNextPassagesUseCase: GetNextPassagesUseCase,
    private val rtRepository: GtfsRtRepository
) : ViewModel() {

    private data class TripWithNames(
        val trip: PinnedTrip,
        val departureName: String,
        val arrivalName: String
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val pinnedTripsState: StateFlow<List<PinnedTripUiState>> = pinnedTripDao.getAllPinnedTripsWithNames()
        .map { list ->
            list.map { item ->
                val trip = item.pinnedTrip.toDomain()
                TripWithNames(
                    trip = trip,
                    departureName = item.departureStopName ?: trip.departureStopId,
                    arrivalName = item.arrivalStopName ?: trip.arrivalStopId
                )
            }
        }
        .flatMapLatest { tripsWithNames ->
            if (tripsWithNames.isEmpty()) return@flatMapLatest flowOf(emptyList())
            
            val flows = tripsWithNames.map { item ->
                getNextPassagesUseCase(item.trip).map { state ->
                    PinnedTripUiState(
                        pinnedTrip = item.trip,
                        departureStopName = item.departureName,
                        arrivalStopName = item.arrivalName,
                        passages = state.passages,
                        dataStatus = state.status
                    )
                }
            }
            combine(flows) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        rtRepository.refresh()
    }

    fun deletePinnedTrip(trip: PinnedTrip) {
        viewModelScope.launch {
            pinnedTripDao.deletePinnedTrip(trip.toEntity())
        }
    }

    private fun PinnedTripEntity.toDomain() = PinnedTrip(
        id = id,
        routeId = routeId,
        departureStopId = departureStopId,
        arrivalStopId = arrivalStopId,
        transportMode = transportMode,
        pinnedAt = pinnedAt
    )

    private fun PinnedTrip.toEntity() = PinnedTripEntity(
        id = id,
        routeId = routeId,
        departureStopId = departureStopId,
        arrivalStopId = arrivalStopId,
        transportMode = transportMode,
        pinnedAt = pinnedAt
    )
}
