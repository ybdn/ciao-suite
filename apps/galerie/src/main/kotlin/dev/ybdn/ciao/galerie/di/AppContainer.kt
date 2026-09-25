package dev.ybdn.ciao.galerie.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.memory.MemoryCache
import dev.ybdn.ciao.galerie.data.datastore.SettingsDataStore
import dev.ybdn.ciao.galerie.data.saf.SafSsdMediaBrowser
import dev.ybdn.ciao.galerie.domain.repository.SsdMediaBrowser
import dev.ybdn.ciao.galerie.domain.usecase.GetMediaDetailsUseCase
import dev.ybdn.ciao.galerie.domain.usecase.GetOriginalUriUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ResolveExternalMediaUseCase
import dev.ybdn.ciao.galerie.data.mediastore.ContentExternalMediaResolver
import dev.ybdn.ciao.galerie.data.mediastore.MediaDetailsReaderImpl
import dev.ybdn.ciao.galerie.domain.usecase.ManageThumbnailCacheUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ObserveSsdAvailabilityUseCase
import dev.ybdn.ciao.galerie.domain.usecase.RefreshSsdIndexUseCase
import dev.ybdn.ciao.galerie.presentation.image.PhoneThumbnailFetcher
import dev.ybdn.ciao.galerie.presentation.image.SsdThumbnailDiskCache
import dev.ybdn.ciao.galerie.presentation.image.SsdThumbnailFetcher
import dev.ybdn.ciao.galerie.data.local.GalerieDatabase
import dev.ybdn.ciao.galerie.data.local.MIGRATION_1_2
import dev.ybdn.ciao.galerie.data.local.MIGRATION_2_3
import dev.ybdn.ciao.galerie.data.repository.TriageRepositoryImpl
import dev.ybdn.ciao.galerie.domain.repository.TriageRepository
import dev.ybdn.ciao.galerie.domain.usecase.DeleteQueuedTriageItemsUseCase
import dev.ybdn.ciao.galerie.domain.usecase.DequeueTriageDeletionUseCase
import dev.ybdn.ciao.galerie.domain.usecase.GetTriageSummaryUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ObserveTriageDeletionQueueUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ObserveTriagePileUseCase
import dev.ybdn.ciao.galerie.domain.usecase.RecordTriageDecisionUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ResetTriageUseCase
import dev.ybdn.ciao.galerie.domain.usecase.UndoLastTriageDecisionUseCase
import dev.ybdn.ciao.galerie.data.mediastore.MediaStoreGallerySource
import dev.ybdn.ciao.galerie.data.mediastore.MediaStoreRepositoryImpl
import dev.ybdn.ciao.galerie.data.repository.RoomFavoritesRepository
import dev.ybdn.ciao.galerie.data.repository.RoomSsdMediaIndex
import dev.ybdn.ciao.galerie.data.repository.RoomTransactionRunner
import dev.ybdn.ciao.galerie.data.repository.TransferStateRepositoryImpl
import dev.ybdn.ciao.galerie.data.saf.SafDestinationWriter
import dev.ybdn.ciao.galerie.domain.model.ScanSession
import dev.ybdn.ciao.galerie.domain.repository.DestinationWriter
import dev.ybdn.ciao.galerie.domain.repository.FavoritesRepository
import dev.ybdn.ciao.galerie.domain.repository.MediaRepository
import dev.ybdn.ciao.galerie.domain.repository.PhoneGallerySource
import dev.ybdn.ciao.galerie.domain.repository.SsdMediaIndex
import dev.ybdn.ciao.galerie.domain.repository.TransactionRunner
import dev.ybdn.ciao.galerie.domain.repository.TransferStateRepository
import dev.ybdn.ciao.galerie.domain.usecase.DeleteVerifiedMediaUseCase
import dev.ybdn.ciao.galerie.domain.usecase.GetDestinationStatusUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ObserveTimelineUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ScanLocalMediaUseCase
import dev.ybdn.ciao.galerie.domain.usecase.TransferMediaUseCase
import dev.ybdn.ciao.galerie.domain.usecase.VerifyTransferUseCase
import dev.ybdn.ciao.galerie.data.mediastore.MediaStoreTrashedSource
import dev.ybdn.ciao.galerie.data.share.FileProviderShareableMediaProvider
import dev.ybdn.ciao.galerie.data.share.ReencodingMetadataStripper
import dev.ybdn.ciao.galerie.domain.usecase.ShareMetadataSettingUseCase
import dev.ybdn.ciao.galerie.domain.repository.TrashedMediaSource
import dev.ybdn.ciao.galerie.domain.usecase.DeleteFromTrashUseCase
import dev.ybdn.ciao.galerie.domain.usecase.DeleteGalleryItemsUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ObserveTrashUseCase
import dev.ybdn.ciao.galerie.domain.usecase.PrepareShareUseCase
import dev.ybdn.ciao.galerie.domain.usecase.RestoreFromTrashUseCase
import dev.ybdn.ciao.galerie.domain.usecase.ToggleFavoriteUseCase
import dev.ybdn.ciao.galerie.presentation.system.IntentSenderLauncher
import dev.ybdn.ciao.galerie.presentation.system.SystemMediaDeletionRequester
import dev.ybdn.ciao.galerie.presentation.system.SystemMediaTrash
import dev.ybdn.ciao.galerie.data.edit.AgslPhotoEditRenderer
import dev.ybdn.ciao.galerie.data.edit.ExifInterfaceMetadataWriter
import dev.ybdn.ciao.galerie.data.edit.FileEditJournal
import dev.ybdn.ciao.galerie.data.edit.FileEditWorkspace
import dev.ybdn.ciao.galerie.data.edit.MediaStorePhoneMediaWriter
import dev.ybdn.ciao.galerie.data.saf.SafSsdMediaWriter
import dev.ybdn.ciao.galerie.domain.usecase.GetEditCapabilitiesUseCase
import dev.ybdn.ciao.galerie.domain.usecase.RecoverInterruptedEditsUseCase
import dev.ybdn.ciao.galerie.domain.usecase.SafeFileEditor
import dev.ybdn.ciao.galerie.domain.usecase.EditMetadataUseCase
import dev.ybdn.ciao.galerie.domain.usecase.OriginalWorkFiles
import dev.ybdn.ciao.galerie.domain.model.LocationClipboard
import dev.ybdn.ciao.galerie.domain.usecase.SavePhotoEditUseCase
import dev.ybdn.ciao.galerie.presentation.system.SystemMediaWriteAccess
import dev.ybdn.ciao.galerie.service.ServiceTransferActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Conteneur d'injection manuelle : pas de framework DI (Hilt jugé non nécessaire pour une app
 * de cette taille), câblage explicite domain/data/presentation ici.
 */
class AppContainer(private val context: Context) {

    /** Portée des flux partagés entre écrans (chronologie), vivant aussi longtemps que le processus. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsDataStore = SettingsDataStore(context)

    private val database = Room.databaseBuilder(
        context,
        GalerieDatabase::class.java,
        GalerieDatabase.DATABASE_NAME,
    )
        // Migrations explicites uniquement : l'état de transfert doit toujours survivre.
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    val mediaRepository: MediaRepository = MediaStoreRepositoryImpl(context)

    val transferStateRepository: TransferStateRepository =
        TransferStateRepositoryImpl(database.transferStateDao())

    val ssdMediaIndex: SsdMediaIndex = RoomSsdMediaIndex(database.ssdMediaDao())

    val favoritesRepository: FavoritesRepository = RoomFavoritesRepository(database.favoriteDao())

    private val triageRepository: TriageRepository = TriageRepositoryImpl(database.triageStateDao())

    private val transactionRunner: TransactionRunner = RoomTransactionRunner(database)

    val destinationWriter: DestinationWriter = SafDestinationWriter(context) {
        settingsDataStore.destinationRootUri.first()
    }

    private val phoneGallerySource: PhoneGallerySource = MediaStoreGallerySource(context)

    private val ssdMediaBrowser: SsdMediaBrowser = SafSsdMediaBrowser(
        context,
        rootUriProvider = { settingsDataStore.destinationRootUri.first() },
        scope = applicationScope,
    )

    private val ssdThumbnailCache = SsdThumbnailDiskCache(context, applicationScope) {
        settingsDataStore.thumbnailCacheMaxBytes.first()
    }

    /** Confirmations système MediaStore ; chaque activité s'y enregistre à sa création. */
    val intentSenderLauncher = IntentSenderLauncher()

    private val mediaDeletionRequester = SystemMediaDeletionRequester(context, intentSenderLauncher)

    private val mediaTrash = SystemMediaTrash(context, intentSenderLauncher)

    private val trashedMediaSource: TrashedMediaSource = MediaStoreTrashedSource(context)

    val scanSession = ScanSession()

    val scanLocalMediaUseCase = ScanLocalMediaUseCase(mediaRepository, transferStateRepository, scanSession, destinationWriter)

    val getDestinationStatusUseCase = GetDestinationStatusUseCase(destinationWriter)

    private val verifyTransferUseCase = VerifyTransferUseCase(destinationWriter)

    val transferMediaUseCase = TransferMediaUseCase(
        destinationWriter,
        transferStateRepository,
        verifyTransferUseCase,
        ssdMediaIndex,
        favoritesRepository,
        triageRepository,
        ssdThumbnailCache,
        transactionRunner,
    )

    val deleteVerifiedMediaUseCase = DeleteVerifiedMediaUseCase(
        transferStateRepository,
        mediaRepository,
        mediaDeletionRequester,
    )

    val observeTimelineUseCase = ObserveTimelineUseCase(
        phoneGallerySource,
        ssdMediaIndex,
        transferStateRepository,
        favoritesRepository,
        destinationWriter,
        applicationScope,
    )

    val getMediaDetailsUseCase = GetMediaDetailsUseCase(MediaDetailsReaderImpl(context), ssdMediaBrowser)

    // Édition des photos (v3).

    private val editWorkspace = FileEditWorkspace(context)

    private val editJournal = FileEditJournal(context)

    private val safeFileEditor = SafeFileEditor(
        phoneWriter = MediaStorePhoneMediaWriter(context),
        ssdWriter = SafSsdMediaWriter(context) { settingsDataStore.destinationRootUri.first() },
        writeAccess = SystemMediaWriteAccess(context, intentSenderLauncher),
        journal = editJournal,
        transferStateRepository = transferStateRepository,
        ssdMediaIndex = ssdMediaIndex,
        ssdThumbnailCache = ssdThumbnailCache,
        favoritesRepository = favoritesRepository,
        triageRepository = triageRepository,
        transactionRunner = transactionRunner,
    )

    val recoverInterruptedEditsUseCase = RecoverInterruptedEditsUseCase(editJournal, safeFileEditor)

    val getEditCapabilitiesUseCase = GetEditCapabilitiesUseCase(ssdMediaBrowser, ServiceTransferActivity(applicationScope))

    private val metadataWriter = ExifInterfaceMetadataWriter(context)

    private val originalWorkFiles = OriginalWorkFiles(editWorkspace, ssdMediaBrowser)

    val savePhotoEditUseCase = SavePhotoEditUseCase(
        editWorkspace,
        metadataWriter,
        originalWorkFiles,
        AgslPhotoEditRenderer(context),
        safeFileEditor,
    )

    val editMetadataUseCase = EditMetadataUseCase(
        editWorkspace,
        metadataWriter,
        originalWorkFiles,
        getEditCapabilitiesUseCase,
        getMediaDetailsUseCase,
        SystemMediaWriteAccess(context, intentSenderLauncher),
        safeFileEditor,
    )

    val locationClipboard = LocationClipboard()

    val refreshSsdIndexUseCase = RefreshSsdIndexUseCase(ssdMediaBrowser, ssdMediaIndex, applicationScope) {
        recoverInterruptedEditsUseCase()
    }

    val getOriginalUriUseCase = GetOriginalUriUseCase(ssdMediaBrowser)

    val observeSsdAvailabilityUseCase = ObserveSsdAvailabilityUseCase(ssdMediaBrowser)

    val resolveExternalMediaUseCase = ResolveExternalMediaUseCase(ContentExternalMediaResolver(context))

    val manageThumbnailCacheUseCase = ManageThumbnailCacheUseCase(ssdThumbnailCache)

    val deleteGalleryItemsUseCase = DeleteGalleryItemsUseCase(
        mediaTrash,
        ssdMediaBrowser,
        ssdMediaIndex,
        ssdThumbnailCache,
        transferStateRepository,
        favoritesRepository,
        triageRepository,
        transactionRunner,
    )

    // Tri de la pellicule (v4).

    val observeTriagePileUseCase = ObserveTriagePileUseCase(observeTimelineUseCase, triageRepository)

    val recordTriageDecisionUseCase = RecordTriageDecisionUseCase(triageRepository)

    val undoLastTriageDecisionUseCase = UndoLastTriageDecisionUseCase(triageRepository)

    val getTriageSummaryUseCase = GetTriageSummaryUseCase(observeTimelineUseCase, triageRepository)

    val resetTriageUseCase = ResetTriageUseCase(triageRepository)

    val observeTriageDeletionQueueUseCase = ObserveTriageDeletionQueueUseCase(observeTriagePileUseCase)

    val dequeueTriageDeletionUseCase = DequeueTriageDeletionUseCase(triageRepository)

    val deleteQueuedTriageItemsUseCase = DeleteQueuedTriageItemsUseCase(deleteGalleryItemsUseCase, triageRepository)

    val restoreFromTrashUseCase = RestoreFromTrashUseCase(mediaTrash, transferStateRepository, ssdMediaIndex)

    val deleteFromTrashUseCase = DeleteFromTrashUseCase(mediaTrash)

    val observeTrashUseCase = ObserveTrashUseCase(trashedMediaSource)

    val toggleFavoriteUseCase = ToggleFavoriteUseCase(favoritesRepository)

    val prepareShareUseCase = PrepareShareUseCase(
        settingsDataStore,
        FileProviderShareableMediaProvider(context, ReencodingMetadataStripper(context)),
        ssdMediaBrowser,
    )

    val shareMetadataSettingUseCase = ShareMetadataSettingUseCase(settingsDataStore)

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(PhoneThumbnailFetcher.Key())
                add(PhoneThumbnailFetcher.Factory(context.contentResolver))
                add(SsdThumbnailFetcher.Key())
                add(SsdThumbnailFetcher.Factory(context, ssdThumbnailCache, ssdMediaBrowser))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build()
            }
            .build()
    }

    /** Reprise des enregistrements interrompus par l'arrêt de l'app (spec v3 A4). */
    fun recoverInterruptedEdits() {
        applicationScope.launch {
            editWorkspace.deleteStaleFiles()
            recoverInterruptedEditsUseCase()
        }
    }

    /** Les copies partagées lors de la session précédente ne servent plus. */
    fun clearTemporaryShareCopies() {
        applicationScope.launch { prepareShareUseCase.clearTemporaryCopies() }
    }

    private companion object {
        const val MEMORY_CACHE_PERCENT = 0.25
    }
}
