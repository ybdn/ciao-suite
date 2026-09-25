package dev.ybdn.ciao.galerie

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.ybdn.ciao.galerie.di.AppContainer

class GalerieApplication : Application(), SingletonImageLoader.Factory {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.clearTemporaryShareCopies()
        appContainer.recoverInterruptedEdits()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = appContainer.imageLoader
}
