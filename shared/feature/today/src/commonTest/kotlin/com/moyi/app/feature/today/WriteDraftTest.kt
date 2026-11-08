package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.domain.today.Draft
import com.moyi.app.domain.today.DraftKind
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import com.moyi.app.feature.today.write.WriteAction
import com.moyi.app.feature.today.write.WriteEvent
import com.moyi.app.feature.today.write.WriteSubmit
import com.moyi.app.feature.today.write.WriteViewModel
import com.moyi.app.feature.today.write.WriteViewModel.Companion.DRAFT_PAUSE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The composer's drafts (doc 13 §5: local first, always). runTest shares the Main test
 * dispatcher's scheduler, so the keystroke pause runs on virtual time: no real waiting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WriteDraftTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { show(dayView(TodayStatus.NotWritten, myEntry = null)) }
    private val store = FakeDraftStore()
    private var keys = 0

    private fun viewModel(editing: Boolean = false) = WriteViewModel(editing, loop, store, newIdempotencyKey = { "key-${++keys}" })

    private fun written(text: String = MINE) = loop.show(dayView(TodayStatus.Waiting, myEntry = text))

    private fun WriteViewModel.type(text: String) = onAction(WriteAction.OnTextChange(text))

    @Test
    fun theDraftIsWrittenOnAPauseNotOnEveryKeystroke() = runTest {
        val vm = viewModel()
        for (typed in listOf("Y", "Yo", "You", "You made")) {
            vm.type(typed)
            advanceTimeBy(DRAFT_PAUSE / 2)
        }
        assertEquals(0, store.writes, "no write while the typing keeps going")

        advanceTimeBy(DRAFT_PAUSE / 2 + 1)
        assertEquals(1, store.writes, "one write once it pauses")
        assertEquals(Draft("bond-1", DraftKind.NewEntry, "You made", DAY.toString()), store[DraftKind.NewEntry])
    }

    @Test
    fun reopeningWriteRestoresTheDraft() = runTest {
        viewModel().type("You made the tea.")
        advanceTimeBy(DRAFT_PAUSE + 1)

        // A fresh composer, as after closing the app, a force-quit or a crash.
        val reopened = viewModel()
        runCurrent()
        assertFalse(reopened.state.value.restoringDraft)
        assertEquals("You made the tea.", reopened.state.value.text)
        assertEquals(PROMPT.text, reopened.state.value.prompt, "the prompt is still offered")
        assertTrue(reopened.state.value.wouldLoseWords, "closing still asks first (D10)")
    }

    @Test
    fun withNoDraftTheComposerOpensEmpty() = runTest {
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.state.value.restoringDraft)
        assertEquals("", vm.state.value.text)
    }

    @Test
    fun aNewEntryDraftAndAnEditDraftNeverOverwriteEachOther() = runTest {
        store.drafts["bond-1" to DraftKind.NewEntry] = Draft("bond-1", DraftKind.NewEntry, "A new entry.", DAY.toString())
        written()

        val edit = viewModel(editing = true)
        runCurrent()
        assertEquals(MINE, edit.state.value.text, "the new entry's draft is not the edit's")
        edit.type("$MINE And the toast.")
        advanceTimeBy(DRAFT_PAUSE + 1)

        assertEquals(Draft("bond-1", DraftKind.Edit, "$MINE And the toast.", DAY.toString(), entryId = "entry-1"), store[DraftKind.Edit])
        assertEquals("A new entry.", store[DraftKind.NewEntry]?.text)

        val reopened = viewModel(editing = true)
        runCurrent()
        assertEquals("$MINE And the toast.", reopened.state.value.text)
        assertEquals(MINE, reopened.state.value.originalText, "what is saved is still what the server has")
    }

    @Test
    fun anEditDraftForAnotherEntryIsNotRestored() = runTest {
        store.drafts["bond-1" to DraftKind.Edit] = Draft("bond-1", DraftKind.Edit, "Yesterday's change.", "2026-09-13", entryId = "entry-0")
        written()

        val vm = viewModel(editing = true)
        runCurrent()
        assertEquals(MINE, vm.state.value.text, "its Save would change the wrong entry")
    }

    @Test
    fun aNewEntryDraftFromAnEarlierDayIsStillRestored() = runTest {
        store.drafts["bond-1" to DraftKind.NewEntry] = Draft("bond-1", DraftKind.NewEntry, "Monday's words.", "2026-09-13")
        loop.show(dayView(TodayStatus.NotWritten, myEntry = null, date = LocalDate(2026, 9, 14)))

        val vm = viewModel()
        runCurrent()
        assertEquals("Monday's words.", vm.state.value.text, "unsent words are never dropped")
    }

    @Test
    fun aSuccessfulSendDeletesTheDraftEvenWithAWriteStillWaiting() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.type("You made the tea.")
            advanceTimeBy(DRAFT_PAUSE + 1)
            vm.type("You made the tea again.")
            // Sent before the pause: the waiting write must not land after the delete.
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        advanceUntilIdle()
        assertNull(store[DraftKind.NewEntry])
    }

    @Test
    fun aSuccessfulEditSaveDeletesOnlyTheEditDraft() = runTest {
        store.drafts["bond-1" to DraftKind.NewEntry] = Draft("bond-1", DraftKind.NewEntry, "A new entry.", DAY.toString())
        written()
        val vm = viewModel(editing = true)
        vm.events.test {
            vm.type("$MINE And the toast.")
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        advanceUntilIdle()
        assertNull(store[DraftKind.Edit])
        assertEquals("A new entry.", store[DraftKind.NewEntry]?.text)
    }

    @Test
    fun aFailedSendKeepsTheDraftWrittenAtTheTap() = runTest {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        vm.type("You made the tea.")
        vm.onAction(WriteAction.OnSubmit) // before any pause: the tap itself keeps the words
        runCurrent()

        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        assertEquals("You made the tea.", store[DraftKind.NewEntry]?.text)
        advanceUntilIdle()
        assertEquals("You made the tea.", store[DraftKind.NewEntry]?.text)
    }

    @Test
    fun theDraftIsKeptWhileTheSendIsInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        loop.gate = gate
        val vm = viewModel()
        vm.type("You made the tea.")
        vm.onAction(WriteAction.OnSubmit)
        runCurrent()
        assertEquals("You made the tea.", store[DraftKind.NewEntry]?.text, "if the app dies now, the words are here")
        gate.complete(Unit)
    }

    @Test
    fun discardingDeletesTheDraft() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.type("You made the tea.")
            advanceTimeBy(DRAFT_PAUSE + 1)
            vm.onAction(WriteAction.OnCloseClick)
            assertTrue(vm.state.value.confirmingDiscard)
            vm.onAction(WriteAction.OnDiscardConfirm)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertNull(store[DraftKind.NewEntry])
    }

    @Test
    fun keepAsADraftClosesAndTheWordsComeBackNextTime() = runTest {
        val vm = viewModel()
        vm.events.test {
            // Closed before the pause: Keep as a draft writes the words now, not after it.
            vm.type("You made the tea.")
            vm.onAction(WriteAction.OnCloseClick)
            assertTrue(vm.state.value.confirmingDiscard)
            vm.onAction(WriteAction.OnKeepDraft)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertFalse(vm.state.value.confirmingDiscard)
        assertEquals("You made the tea.", store[DraftKind.NewEntry]?.text)
        assertTrue(loop.submits.isEmpty(), "keeping is not sending")

        val reopened = viewModel()
        runCurrent()
        assertEquals("You made the tea.", reopened.state.value.text)
    }

    @Test
    fun keepAsADraftKeepsAnEditDraftForItsEntry() = runTest {
        written()
        val vm = viewModel(editing = true)
        vm.events.test {
            vm.type("$MINE And the toast.")
            vm.onAction(WriteAction.OnCloseClick)
            vm.onAction(WriteAction.OnKeepDraft)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertEquals("$MINE And the toast.", store[DraftKind.Edit]?.text)

        val reopened = viewModel(editing = true)
        runCurrent()
        assertEquals("$MINE And the toast.", reopened.state.value.text)
    }

    @Test
    fun emptyingTheFieldLeavesNoDraft() = runTest {
        val vm = viewModel()
        vm.type("You made the tea.")
        advanceTimeBy(DRAFT_PAUSE + 1)
        vm.type("   ")
        advanceTimeBy(DRAFT_PAUSE + 1)
        assertNull(store[DraftKind.NewEntry])
    }

    @Test
    fun anEditBackToTheSavedWordsLeavesNoDraftAndClosesWithoutAsking() = runTest {
        written()
        val vm = viewModel(editing = true)
        vm.events.test {
            vm.type("$MINE And the toast.")
            advanceTimeBy(DRAFT_PAUSE + 1)
            vm.type(MINE)
            vm.onAction(WriteAction.OnCloseClick)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertNull(store[DraftKind.Edit])
    }

    @Test
    fun aBrokenDraftStoreNeverStopsTheComposer() = runTest {
        store.broken = true
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.state.value.restoringDraft)
        vm.events.test {
            vm.type("You made the tea.")
            advanceTimeBy(DRAFT_PAUSE + 1)
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertEquals("You made the tea.", loop.submits.single().text)
    }
}
