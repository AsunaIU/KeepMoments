package com.example.myapplication

import android.app.Application

class KeepMomentsApplication : Application() {
    val container: AppContainer by lazy {
        AppContainer(this)
    }
}
