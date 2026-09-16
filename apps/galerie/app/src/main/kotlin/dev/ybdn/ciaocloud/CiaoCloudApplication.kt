package dev.ybdn.ciaocloud

import android.app.Application
import dev.ybdn.ciaocloud.di.AppContainer

class CiaoCloudApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
