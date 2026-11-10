import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging
import Shared

/// Receives what iOS tells the app about its notifications (UNUserNotificationCenterDelegate),
/// and connects APNs to FCM (MessagingDelegate).
///
/// The delegate must be set before `didFinishLaunching` returns: a tap that launched the app
/// (a cold start) is delivered to it right after, and one set later never hears of that tap.
/// A tap while the app is in the background (a warm start) reaches the same method.
///
/// **Push (FCM).** Apple delivers every push to an iPhone through APNs; FCM sits in front of
/// it, so the server needs only one kind of token for both platforms. The chain at launch:
/// `registerForRemoteNotifications` → APNs answers with its device token → handed to FCM
/// (`Messaging.messaging().apnsToken`) → FCM issues its own token → the delegate passes it
/// to Kotlin (`pushTokenReceived`), whose `PushTokenRegistrar` tells the server. Registering
/// asks the person nothing: whether a push may show is the permission Bond settings asks for.
/// Swizzling is off (Info.plist `FirebaseAppDelegateProxyEnabled`), so each step is here.
///
/// **Without `GoogleService-Info.plist`** in the bundle (no file in iosApp/Firebase/ for this
/// configuration: README, "Push notifications") Firebase is never configured, nothing is
/// registered, and the app runs without push. `FirebaseApp.configure()` would crash without it.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            application.registerForRemoteNotifications()
        } else {
            print("Push: no GoogleService-Info.plist in this build, so no push")
        }
        return true
    }

    /// APNs gave this device a token: FCM needs it before it can issue its own. Never logged.
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    /// No APNs token (no network, or no Push Notifications entitlement in this signing).
    /// The app works on without push; iOS tries again at the next launch.
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Push: APNs registration failed: \((error as NSError).code)")
    }

    /// FCM issued or rotated this device's token: Kotlin tells the server (PushTokenHolder,
    /// PushTokenRegistrar). The token is a secret device identifier: never logged.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        PushTokensKt.pushTokenReceived(token: fcmToken)
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
