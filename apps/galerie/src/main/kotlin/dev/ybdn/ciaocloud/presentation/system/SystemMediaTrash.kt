package dev.ybdn.ciaocloud.presentation.system

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.ybdn.ciaocloud.domain.repository.MediaTrash

/** Corbeille système via `MediaStore.createTrashRequest` / `createDeleteRequest`, avec confirmation. */
class SystemMediaTrash(
    private val context: Context,
    private val intentSenderLauncher: IntentSenderLauncher,
) : MediaTrash {

    override suspend fun moveToTrash(uris: List<String>): Boolean =
        intentSenderLauncher.launch(MediaStore.createTrashRequest(context.contentResolver, uris.map(Uri::parse), true))

    override suspend fun restore(uris: List<String>): Boolean =
        intentSenderLauncher.launch(MediaStore.createTrashRequest(context.contentResolver, uris.map(Uri::parse), false))

    override suspend fun deletePermanently(uris: List<String>): Boolean =
        intentSenderLauncher.launch(MediaStore.createDeleteRequest(context.contentResolver, uris.map(Uri::parse)))
}
