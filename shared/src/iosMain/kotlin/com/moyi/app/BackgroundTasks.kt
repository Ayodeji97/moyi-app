package com.moyi.app

import com.moyi.app.data.repository.IosOutboxScheduler
import com.moyi.app.data.repository.OutboxBackgroundRunner
import com.moyi.app.data.repository.OutboxScheduler
import com.moyi.app.domain.today.OutboxPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler

private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Registers the outbox's background task. Swift calls it from `iOSApp.init`, because
 * BGTaskScheduler accepts a registration only before the app finishes launching. Starts
 * Koin if it is not running yet (a background launch has no view controller to start it).
 *
 * The task makes one pass ([OutboxBackgroundRunner]). If something is still waiting it asks
 * for another task, and iOS decides when, if ever, to grant it. If iOS takes the time back
 * first, the pass is cancelled: a request already sent is answered by its key on the next try.
 */
fun registerBackgroundTasks() {
    if (KoinPlatform.getKoinOrNull() == null) initKoin()
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(IosOutboxScheduler.TASK_ID, usingQueue = null) { task ->
        task?.let(::runOutboxTask)
    }
}

private fun runOutboxTask(task: BGTask) {
    val koin = KoinPlatform.getKoin()
    val job = backgroundScope.launch {
        val pass = try {
            koin.get<OutboxBackgroundRunner>().run()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            OutboxPass.Waiting
        }
        if (pass == OutboxPass.Waiting) koin.get<OutboxScheduler>().schedule()
        task.setTaskCompletedWithSuccess(pass != OutboxPass.Waiting)
    }
    task.expirationHandler = {
        job.cancel()
        task.setTaskCompletedWithSuccess(false)
    }
}
