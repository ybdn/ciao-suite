package dev.ybdn.ciaocloud.data.local

import androidx.room.TypeConverter
import dev.ybdn.ciaocloud.domain.model.MediaType
import dev.ybdn.ciaocloud.domain.model.TransferStatus
import dev.ybdn.ciaocloud.domain.model.TriageDecision

class Converters {
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus = TransferStatus.valueOf(value)

    @TypeConverter
    fun fromTriageDecision(value: TriageDecision): String = value.name

    @TypeConverter
    fun toTriageDecision(value: String): TriageDecision = TriageDecision.valueOf(value)
}
