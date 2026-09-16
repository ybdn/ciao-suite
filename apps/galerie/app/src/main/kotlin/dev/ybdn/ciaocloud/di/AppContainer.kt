package dev.ybdn.ciaocloud.di

import android.content.Context
import androidx.room.Room
import dev.ybdn.ciaocloud.data.datastore.SettingsDataStore
import dev.ybdn.ciaocloud.data.local.CiaoCloudDatabase
import dev.ybdn.ciaocloud.data.mediastore.MediaStoreRepositoryImpl
import dev.ybdn.ciaocloud.data.repository.TransferStateRepositoryImpl
import dev.ybdn.ciaocloud.data.saf.SafDestinationWriter
import dev.ybdn.ciaocloud.domain.repository.DestinationWriter
import dev.ybdn.ciaocloud.domain.repository.MediaRepository
import dev.ybdn.ciaocloud.domain.repository.TransferStateRepository
import dev.ybdn.ciaocloud.domain.usecase.DeleteVerifiedMediaUseCase
import dev.ybdn.ciaocloud.domain.usecase.ScanLocalMediaUseCase
import dev.ybdn.ciaocloud.domain.usecase.TransferMediaUseCase
import dev.ybdn.ciaocloud.domain.usecase.VerifyTransferUseCase
import dev.ybdn.ciaocloud.presentation.delete.SystemMediaDeletionRequester
import kotlinx.coroutines.flow.first

/**
 * Conteneur d'injection manuelle : pas de framework DI (Hilt jugé non nécessaire pour une app
 * de cette taille), câblage explicite domain/data/presentation ici.
 */
class AppContainer(private val context: Context) {

    val settingsDataStore = SettingsDataStore(context)

    private val database = Room.databaseBuilder(
        context,
        CiaoCloudDatabase::class.java,
        CiaoCloudDatabase.DATABASE_NAME,
    ).build()

    val mediaRepository: MediaRepository = MediaStoreRepositoryImpl(context)

    val transferStateRepository: TransferStateRepository =
        TransferStateRepositoryImpl(database.transferStateDao())

    val destinationWriter: DestinationWriter = SafDestinationWriter(context) {
        settingsDataStore.destinationRootUri.first()
    }

    val mediaDeletionRequester = SystemMediaDeletionRequester(context)

    val scanLocalMediaUseCase = ScanLocalMediaUseCase(mediaRepository, transferStateRepository)

    private val verifyTransferUseCase = VerifyTransferUseCase(destinationWriter)

    val transferMediaUseCase = TransferMediaUseCase(
        destinationWriter,
        transferStateRepository,
        verifyTransferUseCase,
    )

    val deleteVerifiedMediaUseCase = DeleteVerifiedMediaUseCase(
        transferStateRepository,
        mediaDeletionRequester,
    )
}
