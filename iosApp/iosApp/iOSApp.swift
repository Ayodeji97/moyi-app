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
        #if DEBUG
        // DEBUG ONLY: `xcrun simctl launch booted app.getmoyi.moyi -moyiOpenLink <url>` hands
        // <url> over as if it were a universal link, to exercise the routing on a simulator
        // while the site's AASA lists no paths. Release builds do not compile this.
        let arguments = ProcessInfo.processInfo.arguments
        if let flag = arguments.firstIndex(of: "-moyiOpenLink"), flag + 1 < arguments.count {
            LinksKt.linkOpened(url: arguments[flag + 1])
        }
        #endif
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // A universal link to getmoyi.app (the Associated Domains entitlement and the
                // site's AASA), cold or warm. The shell parses and routes it (LinkInbox); the
                // URL is never logged, as it may carry a token.
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    if let url = activity.webpageURL {
                        LinksKt.linkOpened(url: url.absoluteString)
                    }
                }
        }
    }
}
