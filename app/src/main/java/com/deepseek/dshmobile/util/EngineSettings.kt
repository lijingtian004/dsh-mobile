package com.deepseek.dshmobile.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.engineDataStore by preferencesDataStore(name = "engine_config")

data class EngineConfig(
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelId: String = ""
)

object EngineSettings {

    private val KEY_API_KEY = stringPreferencesKey("api_key")
    private val KEY_BASE_URL = stringPreferencesKey("base_url")
    private val KEY_MODEL_ID = stringPreferencesKey("model_id")

    suspend fun load(context: Context): EngineConfig {
        val prefs = context.engineDataStore.data.first()
        return EngineConfig(
            apiKey = prefs[KEY_API_KEY] ?: "",
            baseUrl = prefs[KEY_BASE_URL] ?: "",
            modelId = prefs[KEY_MODEL_ID] ?: ""
        )
    }

    suspend fun save(context: Context, config: EngineConfig) {
        context.engineDataStore.edit { prefs ->
            prefs[KEY_API_KEY] = config.apiKey
            prefs[KEY_BASE_URL] = config.baseUrl
            prefs[KEY_MODEL_ID] = config.modelId
        }
    }
}
