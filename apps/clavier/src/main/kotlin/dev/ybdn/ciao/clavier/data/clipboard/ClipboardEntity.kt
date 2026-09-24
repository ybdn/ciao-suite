package dev.ybdn.ciao.clavier.data.clipboard

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ybdn.ciao.clavier.domain.clipboard.ClipboardItem

@Entity(tableName = "clipboard_item", indices = [Index(value = ["text"], unique = true)])
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val copiedAt: Long,
    val pinned: Boolean = false,
) {
    fun toDomain() = ClipboardItem(id = id, text = text, copiedAt = copiedAt, pinned = pinned)
}
