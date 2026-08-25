package com.deepseek.dshmobile

import android.app.Application
import com.deepseek.dshmobile.service.DshEngineManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DSHApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DshEngineManager.init(this)
    }
}
