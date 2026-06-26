package com.jb.ileviatime.domain.model

data class TripPassage(
    val tripId: String,
    val departure: PassageTime,
    val arrival: PassageTime
)

sealed class PassageTime {
    data class RealTime(val epochSeconds: Long) : PassageTime()
    data class Scheduled(val epochSeconds: Long) : PassageTime()
    object NotAvailable : PassageTime()
}
