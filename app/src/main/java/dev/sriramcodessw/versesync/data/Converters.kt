package dev.sriramcodessw.versesync.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromFloatList(value: List<Float>?): String? = value?.joinToString(separator = ",")

    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        if (value.isNullOrBlank()) return null
        return value
            .split(',')
            .mapNotNull { it.trim().toFloatOrNull() }
    }
}
