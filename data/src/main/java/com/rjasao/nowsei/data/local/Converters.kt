package com.rjasao.nowsei.data.local

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converters to allow Room to reference complex data types.
 */
class Converters {
    /**
     * Converts a Long timestamp into a Date object.
     * Room will use this when reading from the database.
     */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /**
     * Converts a Date object into a Long timestamp.
     * Room will use this when writing to the database.
     */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
