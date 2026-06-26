package com.jb.ileviatime.data.repository

import com.google.transit.realtime.FeedMessage
import com.jb.ileviatime.di.ApplicationScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GtfsRtRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val externalScope: CoroutineScope
) {
    private val RT_URL = "https://proxy.transport.data.gouv.fr/resource/ilevia-lille-gtfs-rt"
    private val _refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val feedFlow: Flow<Result<FeedMessage>> = _refreshTrigger.flatMapLatest {
        flow {
            while (true) {
                emit(fetchFeed())
                delay(30_000)
            }
        }
    }.shareIn(
        scope = externalScope,
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 1
    )

    fun refresh() {
        _refreshTrigger.tryEmit(Unit)
    }

    private suspend fun fetchFeed(): Result<FeedMessage> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(RT_URL).build()
        try {
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val feed = FeedMessage.parseFrom(response.body?.byteStream())
                Result.success(feed)
            } else {
                Result.failure(Exception("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
