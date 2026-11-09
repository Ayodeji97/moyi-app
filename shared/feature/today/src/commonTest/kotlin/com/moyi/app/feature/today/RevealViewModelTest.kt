package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.common.ReactResult
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.reveal.RevealAction
import com.moyi.app.feature.today.reveal.RevealEvent
import com.moyi.app.feature.today.reveal.RevealPhase
import com.moyi.app.feature.today.reveal.RevealViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class RevealViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { show(dayView(TodayStatus.Revealed)) }

    @Test
    fun opensOnTheBeforeFrameWithMyWordsAndWithoutThePartners() {
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        assertEquals(RevealPhase.Before, vm.state.value.phase)
        assertEquals(MINE, vm.state.value.myEntry)
        assertEquals(PARTNER, vm.state.value.partnerName)
        assertEquals(DAY, vm.state.value.date)
        assertNull(vm.state.value.partnerEntry)
    }

    @Test
    fun turnsToThePartnersWordsAndSettles() = runTest {
        val vm = RevealViewModel(loop, beforeHold = Duration.ZERO, afterHold = Duration.ZERO)
        vm.events.test {
            assertEquals(RevealEvent.Settle, awaitItem())
        }
        assertEquals(RevealPhase.After, vm.state.value.phase)
        assertEquals(THEIRS, vm.state.value.partnerEntry)
    }

    @Test
    fun backBeforeTheTurnSettlesAtOnce() = runTest {
        // D12: Back ends the moment early, into Today · revealed, never out of the app.
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        vm.events.test {
            vm.onAction(RevealAction.OnBack)
            assertEquals(RevealEvent.Settle, awaitItem())
            expectNoEvents()
        }
        assertEquals(RevealPhase.Before, vm.state.value.phase, "settled early: the turn never played")
    }

    @Test
    fun aSecondBackSettlesOnlyOnce() = runTest {
        val vm = RevealViewModel(loop, beforeHold = 1.hours, afterHold = 1.hours)
        vm.events.test {
            vm.onAction(RevealAction.OnBack)
            vm.onAction(RevealAction.OnBack)
            vm.onAction(RevealAction.OnDebugReplay)
            assertEquals(RevealEvent.Settle, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun withNoDayLoadedItShowsNothingOfAnyone() {
        val empty = FakeDailyLoop()
        val vm = RevealViewModel(empty, beforeHold = Duration.ZERO, afterHold = 1.hours)
        assertEquals("", vm.state.value.myEntry)
        assertNull(vm.state.value.partnerEntry)
    }

    // ---- Owner ruling 3 (2026-11-09): the reaction set, once the turn has settled ----

    private val reactable = FakeDailyLoop().apply { show(dayView(TodayStatus.Revealed).copy(canReact = true, myReaction = Reaction.HEART)) }

    @Test
    fun theSetShowsOnlyOnceTheTurnHasSettledAndATapBeforeThenDoesNothing() = runTest {
        val vm = RevealViewModel(reactable, beforeHold = 1.seconds, afterHold = 1.hours, motionSettle = 1.seconds)
        assertTrue(vm.state.value.canReact)
        assertEquals(Reaction.HEART, vm.state.value.myReaction)
        assertFalse(vm.state.value.reactionsShown)

        advanceTimeBy(1.5.seconds)
        assertEquals(RevealPhase.After, vm.state.value.phase)
        assertFalse(vm.state.value.reactionsShown, "the turn is still moving")
        vm.onAction(RevealAction.OnReactionClick(Reaction.SMILE))
        assertTrue(reactable.reactions.isEmpty(), "a tap on the invisible set is ignored")

        advanceTimeBy(1.seconds)
        assertTrue(vm.state.value.reactionsShown)
        vm.onAction(RevealAction.OnReactionClick(Reaction.SMILE))
        vm.onAction(RevealAction.OnReactionClick(Reaction.HEART))
        runCurrent()
        // Another replaces mine; the same one (HEART, still shown: the fake does not publish) takes it back.
        assertEquals(listOf<Reaction?>(Reaction.SMILE, null), reactable.reactions)
    }

    @Test
    fun noSetWhereThePartnersEntryTakesNoReaction() = runTest {
        val vm = RevealViewModel(loop, beforeHold = Duration.ZERO, afterHold = 1.hours, motionSettle = Duration.ZERO)
        runCurrent()
        assertTrue(vm.state.value.reactionsShown)
        assertFalse(vm.state.value.canReact)
        vm.onAction(RevealAction.OnReactionClick(Reaction.HEART))
        runCurrent()
        assertTrue(loop.reactions.isEmpty())
    }

    @Test
    fun aFailedReactionIsSaid() = runTest {
        reactable.reactResult = ReactResult.Failed
        val vm = RevealViewModel(reactable, beforeHold = Duration.ZERO, afterHold = 1.hours, motionSettle = Duration.ZERO)
        runCurrent()
        vm.onAction(RevealAction.OnReactionClick(Reaction.SMILE))
        runCurrent()
        assertTrue(vm.state.value.reactionFailed)
    }

    @Test
    fun theMomentWaitsForAReactionOnItsWayBeforeItSettles() = runTest {
        val gate = CompletableDeferred<Unit>().also { reactable.gate = it }
        val vm = RevealViewModel(reactable, beforeHold = Duration.ZERO, afterHold = 2.seconds, motionSettle = Duration.ZERO)
        vm.events.test {
            runCurrent()
            vm.onAction(RevealAction.OnReactionClick(Reaction.SMILE))
            advanceTimeBy(3.seconds)
            vm.onAction(RevealAction.OnBack)
            runCurrent()
            expectNoEvents()
            gate.complete(Unit)
            assertEquals(RevealEvent.Settle, awaitItem())
            expectNoEvents()
        }
    }
}
