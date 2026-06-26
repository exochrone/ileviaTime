package com.jb.ileviatime.ui.viewmodel

import com.jb.ileviatime.domain.model.DataStatus
import com.jb.ileviatime.domain.model.PinnedTrip
import com.jb.ileviatime.domain.model.TripPassage

data class PinnedTripUiState(
    val pinnedTrip: PinnedTrip,
    val departureStopName: String,
    val arrivalStopName: String,
    val passages: List<TripPassage> = emptyList(),
    val dataStatus: DataStatus = DataStatus.Loading
)
