package com.example

import android.app.Application
import com.example.di.AppContainer

class JarvisApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Note: App Check is initialized strictly inside MainActivity.onCreate(savedInstanceState)
        // with the launch intent so FIREBASE_APPCHECK_DEBUG_TOKEN is captured properly.
        container = AppContainer(this)
    }
}
