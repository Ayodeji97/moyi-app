package com.moyi.app.feature.today

import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.common.RevealLedger
import com.moyi.app.feature.today.common.ReactResult
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.nextReaction
import com.moyi.app.feature.today.common.showsReactionHint
import com.moyi.app.feature.today.today.TodayAction
import com.moyi.app.feature.today.today.TodayViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The reaction set on Today (FR-048): what a tap asks the loop for, one at a time, and a failure said. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReactionsTodayViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop()
    private val viewModel by lazy { TodayViewModel(loop, RevealLedger { "me" }, InviteBonds(), FakeOutbox()) }

    private fun TestScope.open(myReaction: Reaction? = null, partnerReaction: Reaction? = null, status: TodayStatus = TodayStatus.Revealed) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        val revealedOrTombstone = status == TodayStatus.Revealed || status == TodayStatus.Tombstone
        loop.show(
            dayView(status).copy(
                canReact = revealedOrTombstone,
                myReaction = myReaction,
                partnerReaction = partnerReaction,
            ),
        )
    }

    @Test
    fun aTapLeavesItAnotherReplacesItAndTheSameTakesItBack() {
        assertEquals(Reaction.HEART, nextReaction(null, Reaction.HEART))
        assertEquals(Reaction.SMILE, nextReaction(Reaction.HEART, Reaction.SMILE))
        assertNull(nextReaction(Reaction.HEART, Reaction.HEART))
    }

    @Test
    fun theStateCarriesBothReactionsOnARevealedDay() = runTest {
        open(myReaction = Reaction.MOVED, partnerReaction = Reaction.THANK_YOU)
        val state = viewModel.state.value
        assertTrue(state.canReact)
        assertEquals(Reaction.MOVED, state.myReaction)
        assertEquals(Reaction.THANK_YOU, state.partnerReaction)
    }

    @Test
    fun tappingAsksTheLoopForWhatTheTapMeans() = runTest {
        open(myReaction = Reaction.HEART)
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.SMILE))
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.HEART))
        assertEquals(listOf<Reaction?>(Reaction.SMILE, null), loop.reactions)
    }

    @Test
    fun aTapWhileOneIsOnItsWayIsIgnored() = runTest {
        open()
        val gate = CompletableDeferred<Unit>().also { loop.gate = it }
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.HEART))
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.SMILE))
        gate.complete(Unit)
        assertEquals(listOf<Reaction?>(Reaction.HEART), loop.reactions)
    }

    @Test
    fun aFailureIsSaidUnderTheSetAndTheNextTapClearsIt() = runTest {
        open()
        loop.reactResult = ReactResult.Failed
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.HEART))
        assertTrue(viewModel.state.value.reactionFailed)

        loop.reactResult = ReactResult.Done
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.HEART))
        assertFalse(viewModel.state.value.reactionFailed)
    }

    @Test
    fun noLayoutButARevealedOneTakesATap() = runTest {
        open(status = TodayStatus.Waiting)
        assertFalse(viewModel.state.value.canReact)
        viewModel.onAction(TodayAction.OnReactionClick(Reaction.HEART))
        assertTrue(loop.reactions.isEmpty())
    }

    @Test
    fun theHintShowsBeforeOneIsChosenGoesOnceItIsAndReturnsWhenItIsTakenBack() = runTest {
        open()
        assertTrue(showsReactionHint(viewModel.state.value.myReaction), "before: the hint shows")

        // The loop is optimistic: the chosen reaction is in the view the moment it is tapped.
        loop.show(dayView(TodayStatus.Revealed).copy(canReact = true, myReaction = Reaction.MOVED))
        assertEquals(Reaction.MOVED, viewModel.state.value.myReaction)
        assertFalse(showsReactionHint(viewModel.state.value.myReaction), "chosen: the hint is gone")

        loop.show(dayView(TodayStatus.Revealed).copy(canReact = true, myReaction = null))
        assertTrue(showsReactionHint(viewModel.state.value.myReaction), "taken back: the hint returns")
    }
}
