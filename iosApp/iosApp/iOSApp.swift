import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // BGTaskScheduler takes registrations only before launch finishes: the outbox's
        // background send (Info.plist, BGTaskSchedulerPermittedIdentifiers).
        BackgroundTasksKt.registerBackgroundTasks()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
