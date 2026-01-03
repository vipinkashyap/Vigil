package com.openbaby.monitor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val QUALITY = stringPreferencesKey("stream_quality")
        val FRAMERATE = intPreferencesKey("framerate")
        val PORT = intPreferencesKey("port")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
    }

    val streamSettings: Flow<StreamSettings> = context.dataStore.data.map { preferences ->
        StreamSettings(
            quality = preferences[Keys.QUALITY]?.let {
                StreamQuality.entries.find { q -> q.name == it }
            } ?: StreamQuality.MEDIUM,
            framerate = preferences[Keys.FRAMERATE] ?: 30,
            port = preferences[Keys.PORT] ?: 8554,
            audioBitrate = preferences[Keys.AUDIO_BITRATE] ?: 128_000
        )
    }

    suspend fun updateQuality(quality: StreamQuality) {
        context.dataStore.edit { preferences ->
            preferences[Keys.QUALITY] = quality.name
        }
    }

    suspend fun updateFramerate(framerate: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.FRAMERATE] = framerate
        }
    }

    suspend fun updatePort(port: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PORT] = port
        }
    }
}
