package dev.ybdn.ciaocloud.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.memory.MemoryCache
import dev.ybdn.ciaocloud.data.datastore.SettingsDataStore
import dev.ybdn.ciaocloud.data.saf.SafSsdMediaBrowser
import dev.ybdn.ciaocloud.domain.repository.SsdMediaBrowser
import dev.ybdn.ciaocloud.domain.usecase.GetMediaDetailsUseCase
import dev.ybdn.ciaocloud.domain.usecase.GetOriginalUriUseCase
import dev.ybdn.ciaocloud.data.mediastore.MediaDetailsReaderImpl
import dev.ybdn.ciaocloud.domain.usecase.ManageThumbnailCacheUseCase
import dev.ybdn.ciaocloud.domain.usecase.ObserveSsdAvailabilityUseCase
import dev.ybdn.ciaocloud.domain.usecase.RefreshSsdIndexUseCase
import dev.ybdn.ciaocloud.presentation.image.PhoneThumbnailFetcher
import dev.ybdn.ciaocloud.presentation.image.SsdThumbnailDiskCache
import dev.ybdn.ciaocloud.presentation.image.SsdThumbnailFetcher
import dev.ybdn.ciaocloud.data.local.CiaoCloudDatabase
import dev.ybdn.ciaocloud.data.local.MIGRATION_1_2
import dev.ybdn.ciaocloud.data.mediastore.MediaStoreGallerySource
import dev.ybdn.ciaocloud.data.mediastore.MediaStoreRepositoryImpl
import dev.ybdn.ciaocloud.data.repository.RoomFavoritesRepository
import dev.ybdn.ciaocloud.data.repository.RoomSsdMediaIndex
import dev.ybdn.ciaocloud.data.repository.RoomTransactionRunner
import dev.ybdn.ciaocloud.data.repository.TransferStateRepositoryImpl
import dev.ybdn.ciaocloud.data.saf.SafDestinationWriter
import dev.ybdn.ciaocloud.domain.model.ScanSession
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import dev.ybdn.ciaocloud.domain.repository.FavoritesRepository
import dev.ybdn.ciaocloud.domain.repository.MediaRepository
import dev.ybdn.ciaocloud.domain.repository.PhoneGallerySource
import dev.ybdn.ciaocloud.domain.repository.SsdMediaIndex
import dev.ybdn.ciaocloud.domain.repository.TransactionRunner
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import dev.ybdn.ciaocloud.domain.usecase.DeleteVerifiedMediaUseCase
import dev.ybdn.ciaocloud.domain.usecase.GetDestinationStatusUseCase
import dev.ybdn.ciaocloud.domain.usecase.ObserveTimelineUseCase
import dev.ybdn.ciaocloud.domain.usecase.ScanLocalMediaUseCase
import dev.ybdn.ciaocloud.domain.usecase.TransferMediaUseCase
import dev.ybdn.ciaocloud.domain.usecase.VerifyTransferUseCase
import dev.ybdn.ciaocloud.data.mediastore.MediaStoreTrashedSource
import dev.ybdn.ciaocloud.data.share.FileProviderShareableMediaProvider
import dev.ybdn.ciaocloud.domain.repository.TrashedMediaSource
import dev.ybdn.ciaocloud.domain.usecase.DeleteFromTrashUseCase
import dev.ybdn.ciaocloud.domain.usecase.DeleteGalleryItemsUseCase
import dev.ybdn.ciaocloud.domain.usecase.ObserveTrashUseCase
import dev.ybdn.ciaocloud.domain.usecase.PrepareShareUseCase
import dev.ybdn.ciaocloud.domain.usecase.RestoreFromTrashUseCase
import dev.ybdn.ciaocloud.domain.usecase.ToggleFavoriteUseCase
import dev.ybdn.ciaocloud.presentation.system.IntentSenderLauncher
import dev.ybdn.ciaocloud.presentation.system.SystemMediaDeletionRequester
import dev.ybdn.ciaocloud.presentation.system.SystemMediaTrash
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
        CiaoCloudDatabase::class.java,
        CiaoCloudDatabase.DATABASE_NAME,
    )
        // Migrations explicites uniquement : l'état de transfert doit toujours survivre.
        .addMigrations(MIGRATION_1_2)
        .build()

    val mediaRepository: MediaRepository = MediaStoreRepositoryImpl(context)

    val transferStateRepository: TransferStateRepository =
        TransferStateRepositoryImpl(database.transferStateDao())

    val ssdMediaIndex: SsdMediaIndex = RoomSsdMediaIndex(database.ssdMediaDao())

    val favoritesRepository: FavoritesRepository = RoomFavoritesRepository(database.favoriteDao())

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

    val refreshSsdIndexUseCase = RefreshSsdIndexUseCase(ssdMediaBrowser, ssdMediaIndex, applicationScope)

    val getOriginalUriUseCase = GetOriginalUriUseCase(ssdMediaBrowser)

    val observeSsdAvailabilityUseCase = ObserveSsdAvailabilityUseCase(ssdMediaBrowser)

    val getMediaDetailsUseCase = GetMediaDetailsUseCase(MediaDetailsReaderImpl(context), ssdMediaBrowser)

    val manageThumbnailCacheUseCase = ManageThumbnailCacheUseCase(ssdThumbnailCache)

    val deleteGalleryItemsUseCase = DeleteGalleryItemsUseCase(
        mediaTrash,
        ssdMediaBrowser,
        ssdMediaIndex,
        ssdThumbnailCache,
        transferStateRepository,
        favoritesRepository,
        transactionRunner,
    )

    val restoreFromTrashUseCase = RestoreFromTrashUseCase(mediaTrash, transferStateRepository, ssdMediaIndex)

    val deleteFromTrashUseCase = DeleteFromTrashUseCase(mediaTrash)

    val observeTrashUseCase = ObserveTrashUseCase(trashedMediaSource)

    val toggleFavoriteUseCase = ToggleFavoriteUseCase(favoritesRepository)

    val prepareShareUseCase = PrepareShareUseCase(FileProviderShareableMediaProvider(context, ssdMediaBrowser))

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

    /** Les copies partagées lors de la session précédente ne servent plus. */
    fun clearTemporaryShareCopies() {
        applicationScope.launch { prepareShareUseCase.clearTemporaryCopies() }
    }

    private companion object {
        const val MEMORY_CACHE_PERCENT = 0.25
    }
}
