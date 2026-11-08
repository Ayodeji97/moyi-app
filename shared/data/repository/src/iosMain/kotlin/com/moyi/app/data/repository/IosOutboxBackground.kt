package com.moyi.app.data.repository

import kotlinx.cinterop.ExperimentalForeignApi
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * The outbox's background send on iOS: a `BGAppRefreshTask` request, which the system
 * runs **when it chooses** (it weighs battery, how often the app is used, and the
 * network), never at a time the app can name. [EARLIEST_SECONDS] is only "not before".
 * The task itself is registered at launch by the app shell (`registerBackgroundTasks`
 * in :shared, from `iOSApp.init`), and it runs [OutboxBackgroundRunner].
 *
 * So on iOS the dependable sends are the in-process ones: the foreground, and a
 * connection coming back while the app is open ([IosConnectivityWatcher]).
 */
class IosOutboxScheduler : OutboxScheduler {
    @OptIn(ExperimentalForeignApi::class)
    override fun schedule() {
        val request = BGAppRefreshTaskRequest(identifier = TASK_ID)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(EARLIEST_SECONDS)
        // Fails on the simulator and when Background App Refresh is off: then only the
        // foreground sends, which it does anyway. Submitting again replaces the request.
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    }

    companion object {
        /** Listed in iosApp/Info.plist under BGTaskSchedulerPermittedIdentifiers. */
        const val TASK_ID = "com.moyi.app.outbox"
        private const val EARLIEST_SECONDS = 15.0 * 60
    }
}

/** The network path's arrivals, from `NWPathMonitor`: one monitor for the process's life. */
class IosConnectivityWatcher : ConnectivityWatcher {
    private var monitor: nw_path_monitor_t = null

    override fun watch(onOnline: () -> Unit) {
        if (monitor != null) return
        var online: Boolean? = null
        val created = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(created) { path ->
            val now = nw_path_get_status(path) == nw_path_status_satisfied
            if (now && online != true) onOnline()
            online = now
        }
        nw_path_monitor_set_queue(created, dispatch_get_main_queue())
        nw_path_monitor_start(created)
        monitor = created
    }
}
