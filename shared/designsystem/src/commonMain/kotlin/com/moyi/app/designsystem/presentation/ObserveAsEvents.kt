package com.moyi.app.designsystem.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Collects one-off events (navigation, a message to show once) while the
 * screen is at least STARTED.
 *
 * Two details make it safe for a `Channel`-backed flow:
 * - It stops collecting in the background, so an event sent then waits in the
 *   channel instead of navigating a screen nobody can see.
 * - It collects on `Dispatchers.Main.immediate`, so an event is handled in
 *   the same frame it is received and cannot be lost between a recomposition
 *   and the next dispatch.
 */
@Composable
fun <T> ObserveAsEvents(
    events: Flow<T>,
    key1: Any? = null,
    onEvent: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(events, lifecycleOwner.lifecycle, key1) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) {
                events.collect(onEvent)
            }
        }
    }
}
