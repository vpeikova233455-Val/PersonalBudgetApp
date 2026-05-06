package com.budgetapp.data.local.database

import androidx.room.TypeConverter
import com.budgetapp.data.local.entity.*

class Converters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String {
        return value.name
    }

    @TypeConverter
    fun toTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String {
        return value.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return SyncStatus.valueOf(value)
    }

    @TypeConverter
    fun fromRecurrenceFrequency(value: RecurrenceFrequency): String {
        return value.name
    }

    @TypeConverter
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency {
        return RecurrenceFrequency.valueOf(value)
    }

    @TypeConverter
    fun fromImportSource(value: ImportSource): String {
        return value.name
    }

    @TypeConverter
    fun toImportSource(value: String): ImportSource {
        return ImportSource.valueOf(value)
    }
}
