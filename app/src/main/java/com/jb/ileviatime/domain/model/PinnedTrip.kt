package com.jb.ileviatime.domain.model

data class PinnedTrip(
    val id: Int,
    val routeId: String,
    val departureStopId: String,
    val arrivalStopId: String,
    val transportMode: String,
    val pinnedAt: Long
)
