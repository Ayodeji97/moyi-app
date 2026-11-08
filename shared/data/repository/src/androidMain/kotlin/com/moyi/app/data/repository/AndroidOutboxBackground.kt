package com.moyi.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moyi.app.domain.today.OutboxPass
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * The outbox's background send on Android: one unique WorkManager request that runs only
 * with a connected network and backs off exponentially between tries.
 *
 * - **Unique** ([WORK_NAME], [ExistingWorkPolicy.KEEP]): asking again while one is already
 *   waiting keeps that one, so a dozen offline sends never become a dozen workers.
 * - **[NetworkType.CONNECTED]**: WorkManager does not even start the worker offline. When
 *   the connection comes back, the system runs it, with the app open or not.
 * - **Exponential backoff** from [BACKOFF_SECONDS]: each [androidx.work.ListenableWorker.Result.retry]
 *   doubles the wait (WorkManager caps it at five hours).
 *
 * A force-stop from Settings cancels the app's jobs until the app is opened again (the
 * system's rule, not ours); WorkManager re-enqueues them at that launch, and the
 * foreground send runs anyway.
 */
class WorkManagerOutboxScheduler(private val context: Context) : OutboxScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME = "moyi-outbox"
        private const val BACKOFF_SECONDS = 30L
    }
}

/**
 * Runs [OutboxBackgroundRunner] for WorkManager. Built by WorkManager's default factory, so
 * it reaches the app's objects through Koin, which `MoyiApplication.onCreate` starts
 * before any worker can run.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val runner: OutboxBackgroundRunner by inject()

    override suspend fun doWork(): Result = when (runner.run()) {
        OutboxPass.Empty, OutboxPass.SignedOut -> Result.success()
        OutboxPass.Waiting -> Result.retry()
    }
}

/** The default network's arrivals, from [ConnectivityManager]: one callback for the process's life. */
class AndroidConnectivityWatcher(private val context: Context) : ConnectivityWatcher {
    override fun watch(onOnline: () -> Unit) {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onOnline()
            },
        )
    }
}
