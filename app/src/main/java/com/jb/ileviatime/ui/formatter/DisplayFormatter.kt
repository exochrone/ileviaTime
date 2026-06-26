package com.jb.ileviatime.ui.formatter

import com.jb.ileviatime.domain.model.PassageTime
import java.text.SimpleDateFormat
import java.util.*

object DisplayFormatter {

    fun formatPassageTime(time: PassageTime, nowSeconds: Long): String {
        return when (time) {
            is PassageTime.RealTime -> formatEpoch(time.epochSeconds, nowSeconds, isScheduled = false)
            is PassageTime.Scheduled -> formatEpoch(time.epochSeconds, nowSeconds, isScheduled = true)
            is PassageTime.NotAvailable -> "N/A"
        }
    }

    private fun formatEpoch(epoch: Long, nowSeconds: Long, isScheduled: Boolean): String {
        val diffMinutes = (epoch - nowSeconds) / 60
        
        val timeStr = if (diffMinutes in 0..59) {
            "$diffMinutes'"
        } else {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date(epoch * 1000))
        }
        
        return if (isScheduled) "($timeStr)" else timeStr
    }

    fun formatTime(epochSeconds: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(epochSeconds * 1000))
    }
}
