package com.example.data.local.converters

import androidx.room.TypeConverter
import com.example.data.local.entity.JobStatus
import com.example.data.local.entity.JobType

/**
 * Convertisseurs de types Room pour sérialiser et désérialiser les enums et formats spécifiques.
 */
class ToonTypeConverters {
    @TypeConverter
    fun fromJobType(type: JobType?): String? = type?.name

    @TypeConverter
    fun toJobType(value: String?): JobType? = value?.let {
        try {
            JobType.valueOf(it)
        } catch (_: Exception) {
            JobType.ANALYSE
        }
    }

    @TypeConverter
    fun fromJobStatus(status: JobStatus?): String? = status?.name

    @TypeConverter
    fun toJobStatus(value: String?): JobStatus? = value?.let {
        try {
            JobStatus.valueOf(it)
        } catch (_: Exception) {
            JobStatus.QUEUED
        }
    }
}
