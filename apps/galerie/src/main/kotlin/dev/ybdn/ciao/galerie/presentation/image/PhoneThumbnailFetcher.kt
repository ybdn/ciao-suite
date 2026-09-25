package dev.ybdn.ciao.galerie.presentation.image

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/** Vignette système d'un média MediaStore ([uri] content://), à charger via Coil. */
data class PhoneThumbnail(
    val uri: String,
    /** Change quand le média est modifié : invalide la vignette en cache mémoire. */
    val dateModifiedEpochMillis: Long,
)

/**
 * Charge les vignettes générées et mises en cache par le système (`ContentResolver.loadThumbnail`),
 * bien plus rapide que de décoder l'original pour chaque case de la grille.
 */
class PhoneThumbnailFetcher(
    private val data: PhoneThumbnail,
    private val resolver: ContentResolver,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bitmap = resolver.loadThumbnail(Uri.parse(data.uri), Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<PhoneThumbnail> {
        override fun create(data: PhoneThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            PhoneThumbnailFetcher(data, resolver)
    }

    class Key : Keyer<PhoneThumbnail> {
        override fun key(data: PhoneThumbnail, options: Options): String =
            "phone-thumbnail:${data.uri}:${data.dateModifiedEpochMillis}"
    }

    companion object {
        /** Taille unique pour 3 à 5 colonnes : une vignette reste valable après un pincement. */
        const val THUMBNAIL_SIZE_PX = 512
    }
}
