package com.xmen12c.notelink.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val duration = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now())
    val minutes = duration.toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${duration.toHours()}h ago"
        minutes < 60 * 24 * 7 -> "${duration.toDays()}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMillis))
    }
}

fun clockTime(epochMillis: Long): String = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))
