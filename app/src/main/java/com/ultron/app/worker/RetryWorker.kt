package com.ultron.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ultron.app.BuildConfig
import com.ultron.app.data.repository.MessageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class RetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageRepository: MessageRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sent = messageRepository.retrySendPending(
            BuildConfig.TELEGRAM_BOT_TOKEN,
            BuildConfig.TELEGRAM_CHAT_ID
        )
        return if (sent >= 0) Result.success() else Result.retry()
    }

    companion object {
        fun enqueuePeriodicRetry(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<RetryWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ultron_retry",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
