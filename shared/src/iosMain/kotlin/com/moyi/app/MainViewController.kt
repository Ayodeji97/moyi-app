package com.moyi.app

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * The iOS entry point Swift calls. Starts Koin the first time only: SwiftUI
 * may build the view controller more than once, and a second startKoin throws.
 */
fun MainViewController(): UIViewController {
    if (KoinPlatform.getKoinOrNull() == null) initKoin()
    return ComposeUIViewController { App() }
}
