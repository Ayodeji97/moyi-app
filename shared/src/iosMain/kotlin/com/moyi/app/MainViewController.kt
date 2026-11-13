package com.moyi.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS entry point Swift calls. Starts Koin the first time only: SwiftUI
 * may build the view controller more than once, and a second startKoin throws.
 */
fun MainViewController(): UIViewController {
    startKoinOnce()
    return ComposeUIViewController { App() }
}
