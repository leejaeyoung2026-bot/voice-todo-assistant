package com.ultron.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCategory(value: Category?): String? = value?.name

    @TypeConverter
    fun toCategory(value: String?): Category? = value?.let { Category.valueOf(it) }

    @TypeConverter
    fun fromSendStatus(value: SendStatus): String = value.name

    @TypeConverter
    fun toSendStatus(value: String): SendStatus = SendStatus.valueOf(value)
}
