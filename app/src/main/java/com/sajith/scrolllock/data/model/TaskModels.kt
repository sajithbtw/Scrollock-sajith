package com.sajith.scrolllock.data.model

import java.util.Calendar
import java.util.Locale

enum class TaskCategory(val displayLabel: String) {
    REMINDER("Reminder"),
    ALARM("Alarm"),
    STUDY("Study Pages"),
    WORKOUT("AI Workout")
}

object RepeatDays {
    const val MON = 1 shl 0
    const val TUE = 1 shl 1
    const val WED = 1 shl 2
    const val THU = 1 shl 3
    const val FRI = 1 shl 4
    const val SAT = 1 shl 5
    const val SUN = 1 shl 6
    const val WEEKDAYS = MON or TUE or WED or THU or FRI
    const val EVERY_DAY = WEEKDAYS or SAT or SUN

    val ORDERED_FLAGS = listOf(
        MON to "M",
        TUE to "T",
        WED to "W",
        THU to "T",
        FRI to "F",
        SAT to "S",
        SUN to "S"
    )

    fun dayFlagForCalendarDay(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> MON
        Calendar.TUESDAY -> TUE
        Calendar.WEDNESDAY -> WED
        Calendar.THURSDAY -> THU
        Calendar.FRIDAY -> FRI
        Calendar.SATURDAY -> SAT
        Calendar.SUNDAY -> SUN
        else -> 0
    }

    fun formatMask(mask: Int): String {
        if (mask == 0) return "Once"
        if ((mask and EVERY_DAY) == EVERY_DAY) return "Every day"
        if (mask == WEEKDAYS) return "Weekdays"
        if (mask == (SAT or SUN)) return "Weekends"
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val selected = mutableListOf<String>()
        for (i in 0..6) {
            if ((mask and (1 shl i)) != 0) {
                selected.add(names[i])
            }
        }
        return if (selected.isEmpty()) "Once" else selected.joinToString(", ")
    }

    /**
     * Pure deterministic calculation of the next future trigger timestamp in epoch millis
     * matching [hourOfDay]:[minuteOfHour] and [repeatMask].
     */
    fun computeNextTriggerMillis(
        nowMillis: Long,
        hourOfDay: Int,
        minuteOfHour: Int,
        repeatMask: Int
    ): Long {
        val safeHour = hourOfDay.coerceIn(0, 23)
        val safeMin = minuteOfHour.coerceIn(0, 59)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMin)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatMask == 0) {
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        for (offsetDays in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offsetDays)
            }
            val flag = dayFlagForCalendarDay(candidate.get(Calendar.DAY_OF_WEEK))
            if ((repeatMask and flag) != 0 && candidate.timeInMillis > nowMillis) {
                return candidate.timeInMillis
            }
        }

        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun formatTime12h(hourOfDay: Int, minuteOfHour: Int): String {
        val h24 = hourOfDay.coerceIn(0, 23)
        val m = minuteOfHour.coerceIn(0, 59)
        val amPm = if (h24 < 12) "AM" else "PM"
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        return String.format(Locale.US, "%02d:%02d %s", h12, m, amPm)
    }
}

data class FocusTask(
    val id: Long = 0L,
    val title: String,
    val notes: String = "",
    val category: TaskCategory = TaskCategory.REMINDER,
    val triggerTimeMillis: Long = 0L,
    val hourOfDay: Int = 9,
    val minuteOfHour: Int = 0,
    val repeatDaysMask: Int = 0,
    val isEnabled: Boolean = true,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long = 0L,
    val linkedExercise: String = "SQUATS",
    val targetCount: Int = 10,
    val completedCount: Int = 0,
    val requireCompletionToDismiss: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class StudySessionRecord(
    val id: Long = 0L,
    val taskId: Long = -1L,
    val subjectTitle: String,
    val targetPages: Int,
    val pagesTurned: Int,
    val durationSeconds: Int,
    val avgSecondsPerPage: Float,
    val xpEarned: Int,
    val dayKey: String,
    val timestampMs: Long
)
