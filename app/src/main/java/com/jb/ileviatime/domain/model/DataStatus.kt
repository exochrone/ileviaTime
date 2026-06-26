package com.jb.ileviatime.domain.model

sealed class DataStatus {
    object Fresh : DataStatus()
    data class Stale(val lastUpdateAt: Long) : DataStatus()
    object Loading : DataStatus()
}
