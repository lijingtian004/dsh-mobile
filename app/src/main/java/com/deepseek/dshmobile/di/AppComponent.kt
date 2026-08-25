package com.deepseek.dshmobile.di

import android.content.Context
import androidx.room.Room
import com.deepseek.dshmobile.database.AppDatabase
import com.deepseek.dshmobile.repository.SessionRepository
import com.deepseek.dshmobile.repository.SessionRepositoryImpl
import com.deepseek.dshmobile.service.DshEngineManager
import com.deepseek.dshmobile.util.NetworkHelper
import dagger.BindsInstance
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules = [DatabaseModule::class, RepositoryModule::class, ServiceModule::class])
interface AppComponent {
    fun sessionRepository(): SessionRepository
    fun dshEngineManager(): DshEngineManager
    fun networkHelper(): NetworkHelper
    fun database(): AppDatabase

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(app: Context): Builder
        fun build(): AppComponent
    }

    companion object {
        lateinit var instance: AppComponent
            private set

        fun init(app: Context) {
            instance = DaggerAppComponent.builder()
                .application(app)
                .build()
        }
    }
}
