package dev.ybdn.ciaocloud

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dev.ybdn.ciaocloud.di.AppContainer

class CiaoCloudApplication : Application(), SingletonImageLoader.Factory {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.clearTemporaryShareCopies()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = appContainer.imageLoader
}
