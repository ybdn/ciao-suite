package dev.ybdn.ciao.galerie.data.mediastore

import android.net.Uri
import android.provider.MediaStore

/**
 * Collections du volume interne uniquement : le SSD branché en USB est lui aussi indexé par
 * MediaStore et ne doit jamais être scanné (ni proposé à la suppression) comme média du téléphone.
 */
object MediaStoreCollections {
    val IMAGES: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val VIDEO: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val FILES: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
}
