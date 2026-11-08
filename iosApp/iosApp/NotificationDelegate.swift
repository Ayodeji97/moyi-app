import UIKit
import UserNotifications
import Shared

/// Receives what iOS tells the app about its notifications (UNUserNotificationCenterDelegate).
///
/// The delegate must be set before `didFinishLaunching` returns: a tap that launched the app
/// (a cold start) is delivered to it right after, and one set later never hears of that tap.
/// A tap while the app is in the background (a warm start) reaches the same method.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// A push arrived while Moyi is open. Shown as a banner (and in Notification Centre), with
    /// its sound: without this iOS shows nothing while the app is in the foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    /// The person tapped a notification. The shell opens its screen once the app is ready
    /// (PushTapInbox). Dismissing it is not a tap and opens nothing.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
            NotificationTapsKt.notificationTapped(userInfo: response.notification.request.content.userInfo)
        }
        completionHandler()
    }
}
