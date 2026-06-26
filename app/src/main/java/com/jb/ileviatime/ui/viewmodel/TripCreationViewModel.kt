package com.jb.ileviatime.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jb.ileviatime.data.local.dao.GtfsStaticDao
import com.jb.ileviatime.data.local.dao.PinnedTripDao
import com.jb.ileviatime.data.local.entities.PinnedTripEntity
import com.jb.ileviatime.data.local.entities.RouteEntity
import com.jb.ileviatime.data.local.entities.StopEntity
import com.jb.ileviatime.data.repository.GtfsStaticRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripCreationViewModel @Inject constructor(
    private val staticRepository: GtfsStaticRepository,
    private val pinnedTripDao: PinnedTripDao,
    private val staticDao: GtfsStaticDao
) : ViewModel() {

    private val _modes = listOf("BUS", "TRAM")
    val modes: List<String> = _modes

    private val _selectedMode = MutableStateFlow<String?>(null)
    val selectedMode: StateFlow<String?> = _selectedMode

    private val _routes = MutableStateFlow<List<RouteEntity>>(emptyList())
    val routes: StateFlow<List<RouteEntity>> = _routes

    private val _selectedRoute = MutableStateFlow<RouteEntity?>(null)
    val selectedRoute: StateFlow<RouteEntity?> = _selectedRoute

    private val _stops = MutableStateFlow<List<StopEntity>>(emptyList())
    val stops: StateFlow<List<StopEntity>> = _stops

    private val _selectedDepartureStop = MutableStateFlow<StopEntity?>(null)
    val selectedDepartureStop: StateFlow<StopEntity?> = _selectedDepartureStop

    private val _selectedArrivalStop = MutableStateFlow<StopEntity?>(null)
    val selectedArrivalStop: StateFlow<StopEntity?> = _selectedArrivalStop

    fun selectMode(mode: String) {
        _selectedMode.value = mode
        _selectedRoute.value = null
        _selectedDepartureStop.value = null
        _selectedArrivalStop.value = null
        loadRoutes(mode)
    }

    private fun loadRoutes(mode: String) {
        viewModelScope.launch {
            _routes.value = staticRepository.getRoutesByMode(mode == "BUS")
        }
    }

    fun selectRoute(route: RouteEntity) {
        _selectedRoute.value = route
        _selectedDepartureStop.value = null
        _selectedArrivalStop.value = null
        loadStops(route.routeId)
    }

    private fun loadStops(routeId: String) {
        viewModelScope.launch {
            _stops.value = staticRepository.getStopsForRoute(routeId)
        }
    }

    fun selectDepartureStop(stop: StopEntity) {
        _selectedDepartureStop.value = stop
        _selectedArrivalStop.value = null
    }

    fun selectArrivalStop(stop: StopEntity) {
        _selectedArrivalStop.value = stop
    }

    fun pinTrip() {
        val route = _selectedRoute.value ?: return
        val dep = _selectedDepartureStop.value ?: return
        val arr = _selectedArrivalStop.value ?: return
        val mode = _selectedMode.value ?: return

        viewModelScope.launch {
            pinnedTripDao.insertPinnedTrip(
                PinnedTripEntity(
                    routeId = route.routeId,
                    departureStopId = dep.stopId,
                    arrivalStopId = arr.stopId,
                    transportMode = mode,
                    pinnedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
