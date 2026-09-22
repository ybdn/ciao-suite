package dev.ybdn.ciaocloud.presentation.system

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.ybdn.ciaocloud.domain.repository.MediaWriteAccess

/** Droit d'écriture sur des originaux via `MediaStore.createWriteRequest` (confirmation système). */
class SystemMediaWriteAccess(
    private val context: Context,
    private val intentSenderLauncher: IntentSenderLauncher,
) : MediaWriteAccess {

    override suspend fun request(uris: List<String>): Boolean =
        uris.isEmpty() || intentSenderLauncher.launch(MediaStore.createWriteRequest(context.contentResolver, uris.map(Uri::parse)))
}
