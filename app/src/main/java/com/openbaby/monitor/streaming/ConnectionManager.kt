package com.openbaby.monitor.streaming

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ViewerInfo(
    val address: String,
    val connectedAt: Long = System.currentTimeMillis()
)

@Singleton
class ConnectionManager @Inject constructor() {
    companion object {
        private const val TAG = "ConnectionManager"
    }

    private val _viewers = MutableStateFlow<List<ViewerInfo>>(emptyList())
    val viewers: StateFlow<List<ViewerInfo>> = _viewers.asStateFlow()

    val viewerCount: Int
        get() = _viewers.value.size

    fun addViewer(address: String) {
        Log.d(TAG, "Adding viewer: $address")
        _viewers.value = _viewers.value + ViewerInfo(address)
    }

    fun removeViewer(address: String) {
        Log.d(TAG, "Removing viewer: $address")
        _viewers.value = _viewers.value.filterNot { it.address == address }
    }

    fun clearAllViewers() {
        Log.d(TAG, "Clearing all viewers")
        _viewers.value = emptyList()
    }
}
