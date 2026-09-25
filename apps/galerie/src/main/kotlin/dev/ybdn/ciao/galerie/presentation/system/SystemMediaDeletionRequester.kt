package dev.ybdn.ciao.galerie.presentation.system

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dev.ybdn.ciao.galerie.domain.model.MediaType
import dev.ybdn.ciao.galerie.domain.model.TransferRecord
import dev.ybdn.ciao.galerie.domain.repository.MediaDeletionRequester

/**
 * Déclenche `MediaStore.createDeleteRequest()` (API 30+) : l'utilisateur confirme la suppression
 * via une boîte de dialogue système, sans que l'app ait à gérer `RecoverableSecurityException`.
 */
class SystemMediaDeletionRequester(
    private val context: Context,
    private val intentSenderLauncher: IntentSenderLauncher,
) : MediaDeletionRequester {

    override suspend fun requestDelete(records: List<TransferRecord>): Boolean {
        val urisToDelete = records.map { record ->
            val collection = when (record.mediaType) {
                MediaType.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            ContentUris.withAppendedId(collection, record.mediaStoreId)
        }
        return intentSenderLauncher.launch(MediaStore.createDeleteRequest(context.contentResolver, urisToDelete))
    }
}
