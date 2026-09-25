package dev.ybdn.ciaocloud.presentation.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ybdn.ciaocloud.R
import dev.ybdn.ciaocloud.domain.model.GalleryItem
import dev.ybdn.ciao.designsystem.components.NeoNotice
import dev.ybdn.ciao.designsystem.components.NeoTone

/** Accès à l'original d'une page de la visionneuse. */
sealed interface OriginalUri {
    data object Loading : OriginalUri
    data class Available(val uri: String) : OriginalUri
    /** Média présent seulement sur le SSD, débranché. */
    data object Unavailable : OriginalUri
}

/** Résout l'URI de l'original, et la recalcule quand le SSD est branché ou débranché. */
@Composable
fun rememberOriginalUri(item: GalleryItem, ssdAvailable: Boolean, resolve: suspend (GalleryItem) -> String?): OriginalUri {
    // Faux positif du lint Compose : `value` est bien assigné dans le producteur.
    @Suppress("ProduceStateDoesNotAssignValue")
    val state by produceState<OriginalUri>(OriginalUri.Loading, item.key, ssdAvailable) {
        value = resolve(item)?.let { OriginalUri.Available(it) } ?: OriginalUri.Unavailable
    }
    return state
}

@Composable
fun SsdUnpluggedNotice(modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp)) {
        NeoNotice(stringResource(R.string.viewer_plug_ssd), tone = NeoTone.Warning)
    }
}
