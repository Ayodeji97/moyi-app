import SwiftUI
import Shared

@main
struct iOSApp: App {
    // Sets the notification delegate at launch, so a tap that launched the app is not lost.
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

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
