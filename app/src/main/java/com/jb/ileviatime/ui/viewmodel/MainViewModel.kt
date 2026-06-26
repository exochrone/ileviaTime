package com.jb.ileviatime.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.jb.ileviatime.data.repository.GtfsStaticRepository
import com.jb.ileviatime.worker.GtfsStaticSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val staticRepository: GtfsStaticRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isDataReady = MutableStateFlow(staticRepository.isDataAvailable())
    val isDataReady: StateFlow<Boolean> = _isDataReady

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError

    init {
        checkAndSyncData()
    }

    fun retrySync() {
        _syncError.value = null
        startSync()
    }

    private fun checkAndSyncData() {
        viewModelScope.launch {
            if (!staticRepository.isDataAvailable() || staticRepository.shouldUpdate()) {
                startSync()
            } else {
                _isDataReady.value = true
            }
        }
    }

    private fun startSync() {
        val syncRequest = OneTimeWorkRequestBuilder<GtfsStaticSyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("gtfs_sync")
            .build()
        
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            "gtfs_static_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(syncRequest.id).collect { workInfo ->
                val state = workInfo?.state
                _isSyncing.value = state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
                
                if (state == WorkInfo.State.SUCCEEDED) {
                    _isDataReady.value = true
                    _syncError.value = null
                } else if (state == WorkInfo.State.FAILED) {
                    _syncError.value = "Échec du téléchargement des données. Vérifiez votre connexion."
                }
            }
        }
    }
}
