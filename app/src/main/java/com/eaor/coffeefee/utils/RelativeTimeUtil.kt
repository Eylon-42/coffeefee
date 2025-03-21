package com.eaor.coffeefee.utils

import android.text.format.DateUtils
import java.util.Calendar
import java.util.Date

/**
 * Utility class to format timestamps into human-readable relative time spans
 * like "2 minutes ago", "1 hour ago", "Yesterday", etc.
 */
object RelativeTimeUtil {

    /**
     * Returns a human-readable relative time span string for the given timestamp
     *
     * @param date The date to format
     * @return A string representing the relative time (e.g., "just now", "2 minutes ago", "Yesterday")
     */
    fun getRelativeTimeSpan(date: Date): String {
        val now = System.currentTimeMillis()
        val timespan = DateUtils.getRelativeTimeSpanString(
            date.time,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
        
        return timespan.toString()
    }
    
    /**
     * Returns a more detailed time string for exact timestamp display
     *
     * @param date The date to format
     * @return A string representing the date (e.g., "Jan 12, 2023 at 3:45 PM")
     */
    fun getDetailedTimeString(date: Date): String {
        return DateUtils.formatDateTime(
            null,
            date.time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or 
            DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH
        )
    }
    
    /**
     * Checks if the date is from today
     * 
     * @param date The date to check
     * @return True if the date is from today, false otherwise
     */
    fun isToday(date: Date): Boolean {
        val today = Calendar.getInstance()
        val compareDate = Calendar.getInstance()
        compareDate.time = date
        
        return today.get(Calendar.YEAR) == compareDate.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == compareDate.get(Calendar.DAY_OF_YEAR)
    }
} 