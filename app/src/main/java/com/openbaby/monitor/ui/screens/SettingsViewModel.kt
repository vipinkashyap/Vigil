package com.openbaby.monitor.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbaby.monitor.data.SettingsRepository
import com.openbaby.monitor.data.StreamQuality
import com.openbaby.monitor.data.StreamSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<StreamSettings> = settingsRepository.streamSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StreamSettings()
        )

    fun updateQuality(quality: StreamQuality) {
        viewModelScope.launch {
            settingsRepository.updateQuality(quality)
        }
    }

    fun updateFramerate(framerate: Int) {
        viewModelScope.launch {
            settingsRepository.updateFramerate(framerate)
        }
    }

    fun updatePort(port: Int) {
        viewModelScope.launch {
            settingsRepository.updatePort(port)
        }
    }
}
