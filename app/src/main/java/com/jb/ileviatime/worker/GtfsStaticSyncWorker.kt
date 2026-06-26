package com.jb.ileviatime.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.jb.ileviatime.data.repository.GtfsStaticRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GtfsStaticSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: GtfsStaticRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val updated = repository.downloadAndImport()
            if (updated) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
