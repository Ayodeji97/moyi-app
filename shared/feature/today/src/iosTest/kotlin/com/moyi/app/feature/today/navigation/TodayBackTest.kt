@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.today.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.feature.today.di.todayFeatureModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * System back through a real NavHost, on iOS, where it is the left-edge swipe (D3).
 * The swipe follows the arrow exactly, so it must do nothing where there is none:
 * on Today (the root), on Write (a task, D10) and during the reveal (D12).
 *
 * The back event is sent the way the platform sends it, through the navigation
 * event dispatcher, so this checks that the screen's own handler wins over the
 * NavHost's (which would pop Write, dropping the words, and pop the reveal). On
 * Android the same handlers run the screen's close or settle instead; that half is
 * checked on a device (Android Compose UI tests would need Robolectric).
 */
class TodayBackTest {
    private val input = DirectNavigationEventInput()
    private lateinit var nav: NavHostController

    @BeforeTest
    fun startKoinWithTheFakeDay() {
        startKoin { modules(todayFeatureModule) }
    }

    @AfterTest
    fun stopKoinAfter() {
        stopKoin()
    }

    private fun ComposeUiTest.showTheDailyLoop() = setContent {
        val store = remember { ViewModelStore() }
        CompositionLocalProvider(
            LocalViewModelStoreOwner provides object : ViewModelStoreOwner {
                override val viewModelStore = store
            },
            LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null),
        ) {
            val dispatcher: NavigationEventDispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            dispatcher.addInput(input)
            MoyiTheme(darkTheme = true) {
                nav = rememberNavController()
                NavHost(nav, startDestination = TodayRoute) {
                    todayGraph(nav, onOpenSettings = {}, debugTools = false)
                }
            }
        }
    }

    private fun ComposeUiTest.swipeBack() {
        runOnIdle { input.backCompleted() }
        waitForIdle()
    }

    private fun isOn(route: kotlin.reflect.KClass<*>) = nav.currentBackStackEntry!!.destination.hasRoute(route)

    @Test
    fun theEdgeSwipeDoesNothingOnWrite() = runComposeUiTest {
        showTheDailyLoop()
        runOnIdle { nav.navigate(WriteRoute()) }
        waitForIdle()
        assertTrue(isOn(WriteRoute::class))

        swipeBack()

        assertTrue(isOn(WriteRoute::class), "a task has no arrow, so the swipe must not pop it")
    }

    @Test
    fun theEdgeSwipeDoesNothingDuringTheReveal() = runComposeUiTest {
        showTheDailyLoop()
        runOnIdle { nav.openReveal() }
        waitForIdle()
        assertTrue(isOn(RevealRoute::class))

        swipeBack()

        assertTrue(isOn(RevealRoute::class), "the reveal has no arrow and settles on its own")
    }

    @Test
    fun theEdgeSwipeDoesNothingOnToday() = runComposeUiTest {
        showTheDailyLoop()

        swipeBack()

        assertTrue(isOn(TodayRoute::class))
    }
}
