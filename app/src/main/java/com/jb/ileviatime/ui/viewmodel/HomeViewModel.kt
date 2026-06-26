package com.jb.ileviatime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jb.ileviatime.data.local.dao.PinnedTripDao
import com.jb.ileviatime.data.local.entities.PinnedTripEntity
import com.jb.ileviatime.domain.model.PinnedTrip
import com.jb.ileviatime.domain.model.TripPassage
import com.jb.ileviatime.domain.usecase.GetNextPassagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DataStatus {
    object Fresh : DataStatus()
    data class Stale(val lastUpdateAt: Long) : DataStatus()
    object Loading : DataStatus()
}

data class PinnedTripUiState(
    val pinnedTrip: PinnedTrip,
    val passages: List<TripPassage> = emptyList(),
    val dataStatus: DataStatus = DataStatus.Loading
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pinnedTripDao: PinnedTripDao,
    private val getNextPassagesUseCase: GetNextPassagesUseCase
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val pinnedTripsState: StateFlow<List<PinnedTripUiState>> = pinnedTripDao.getAllPinnedTrips()
        .map { entities ->
            entities.map { it.toDomain() }
        }
        .flatMapLatest { trips ->
            if (trips.isEmpty()) return@flatMapLatest flowOf(emptyList())
            
            val flows = trips.map { trip ->
                getNextPassagesUseCase(trip).map { passages ->
                    PinnedTripUiState(
                        pinnedTrip = trip,
                        passages = passages,
                        dataStatus = DataStatus.Fresh // TODO: Handle Stale
                    )
                }
            }
            combine(flows) { it.toList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
