package com.example.myapplication

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class KeepMomentsApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy {
        AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}
