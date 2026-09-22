package dev.ybdn.ciaocloud.presentation.viewer

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ybdn.ciaocloud.CiaoCloudApplication
import dev.ybdn.ciaocloud.domain.model.GalleryFilter
import dev.ybdn.ciaocloud.presentation.theme.CiaoCloudContent

/**
 * Visionneuse ouverte par d'autres apps : `VIEW` (pièce jointe, gestionnaire de fichiers…),
 * `REVIEW` (retour de l'appareil photo) et `REVIEW_SECURE` (appareil photo lancé écran verrouillé).
 */
class ViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as CiaoCloudApplication).appContainer
        appContainer.intentSenderLauncher.register(this)

        val source = sourceFrom(intent) ?: run {
            finish()
            return
        }
        val secure = source is ViewerSource.External && source.secure
        // Écran verrouillé : uniquement les médias transmis par l'appareil photo, sans accès à la galerie.
        if (secure) setShowWhenLocked(true)

        setContent {
            CiaoCloudContent(appContainer.settingsDataStore) {
                ViewerScreen(
                    source = source,
                    onBack = ::finish,
                    guardAction = if (secure) ::runAfterUnlock else { action -> action() },
                )
            }
        }
    }

    private fun sourceFrom(intent: Intent): ViewerSource? {
        val uris = buildList {
            intent.data?.let { add(it.toString()) }
            intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNullTo(this) { clip.getItemAt(it).uri?.toString() } }
        }.distinct()
        if (uris.isEmpty()) return null

        val appContainer = (application as CiaoCloudApplication).appContainer
        return when (intent.action) {
            MediaStore.ACTION_REVIEW_SECURE -> ViewerSource.External(uris, intent.type, secure = true)
            // Appareil photo déverrouillé : la chronologie complète, ouverte sur la dernière prise de vue.
            MediaStore.ACTION_REVIEW -> appContainer.resolveExternalMediaUseCase.timelineKeyOf(uris.first())
                ?.let { key -> ViewerSource.Timeline(key, GalleryFilter.ALL) }
                ?: ViewerSource.External(uris, intent.type, secure = false)
            else -> ViewerSource.External(uris, intent.type, secure = false)
        }
    }

    /** Demande le déverrouillage, puis exécute [action] s'il réussit (immédiatement si déjà déverrouillé). */
    private fun runAfterUnlock(action: () -> Unit) {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (!keyguardManager.isKeyguardLocked) {
            action()
            return
        }
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = action()
            },
        )
    }
}
