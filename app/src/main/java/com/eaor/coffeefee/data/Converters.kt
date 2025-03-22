package com.eaor.coffeefee.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return if (value == null) {
            ""
        } else {
            Gson().toJson(value)
        }
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isEmpty()) {
            return emptyList()
        }
        
        val listType = object : TypeToken<List<String>>() {}.type
        return try {
            Gson().fromJson(value, listType)
        } catch (e: Exception) {
            emptyList()
        }
    }
} 