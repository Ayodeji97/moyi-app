package com.moyi.app.feature.today

import app.cash.turbine.test
import com.moyi.app.feature.today.common.FakeCopy
import com.moyi.app.feature.today.common.TodayStatus
import com.moyi.app.feature.today.common.WriteResult
import com.moyi.app.feature.today.write.WriteAction
import com.moyi.app.feature.today.write.WriteEvent
import com.moyi.app.feature.today.write.WriteState
import com.moyi.app.feature.today.write.WriteSubmit
import com.moyi.app.feature.today.write.WriteViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import com.moyi.app.domain.today.DraftKind

class WriteViewModelTest : MainDispatcherTest() {
    private val loop = FakeDailyLoop().apply { show(dayView(TodayStatus.NotWritten, myEntry = null)) }
    private var keys = 0
    private val drafts = FakeDraftStore()
    private val clock = FixedClock(TAP)
    private fun viewModel(editing: Boolean = false) = WriteViewModel(editing, loop, drafts, newIdempotencyKey = { "key-${++keys}" }, clock = clock)

    private fun written(text: String) = loop.show(dayView(TodayStatus.Waiting, myEntry = text))

    @Test
    fun typingUpdatesTheTextAndTheCounterAppearsOnlyPast400() {
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("a".repeat(400)))
        assertEquals(400, vm.state.value.text.length)
        assertFalse(vm.state.value.showCounter)

        vm.onAction(WriteAction.OnTextChange("a".repeat(401)))
        assertTrue(vm.state.value.showCounter)
        assertFalse(vm.state.value.overLimit)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun over500IsInvalidAndBlankCannotBeSent() {
        assertFalse(WriteState(text = "   ").canSubmit)
        assertFalse(WriteState(text = "").canSubmit)
        val over = WriteState(text = "a".repeat(501))
        assertTrue(over.overLimit)
        assertFalse(over.canSubmit)
        assertTrue(WriteState(text = "a".repeat(500)).canSubmit)
    }

    @Test
    fun dismissingThePromptRemovesIt() {
        val vm = viewModel()
        vm.onAction(WriteAction.OnDismissPrompt)
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun sendingSendsTheWordsWithTodaysPromptAndCloses() = runTest {
        val vm = viewModel()
        assertEquals(PROMPT.text, vm.state.value.prompt)
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertEquals(listOf(FakeDailyLoop.Submit("You made the tea.", PROMPT.id, "key-1", TAP)), loop.submits)
    }

    @Test
    fun aDismissedPromptIsNotSentWithTheEntry() = runTest {
        val vm = viewModel()
        vm.onAction(WriteAction.OnDismissPrompt)
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        assertNull(loop.submits.single().promptId)
    }

    @Test
    fun whileSendingTheActionSaysSoAndASecondTapSendsNothing() {
        val gate = CompletableDeferred<Unit>()
        loop.gate = gate
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(WriteSubmit.Sending, vm.state.value.submit)
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(1, loop.submits.size)
        gate.complete(Unit)
    }

    @Test
    fun aFailedSendKeepsTheWordsAndDoesNotClose() = runTest {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnSubmit)
            expectNoEvents()
        }
        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        assertEquals("You made the tea.", vm.state.value.text)
    }

    @Test
    fun tryAgainWithTheSameWordsIsTheSameSendSoItReusesTheKey() {
        // A lost answer must not become two entries: the server replays a repeated key (doc 06 §1).
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(listOf("key-1", "key-1"), loop.submits.map { it.key })

        // Different words are a different send.
        vm.onAction(WriteAction.OnTextChange("You made the tea, twice."))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals("key-2", loop.submits.last().key)
    }

    @Test
    fun aRetryOfTheSameWordsSendsTheInstantOfTheFirstTapNotANewOne() {
        // intendedAt is part of the body the key is bound to (doc 06 §1).
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        clock.instant = TAP + 3.minutes
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(listOf(TAP, TAP), loop.submits.map { it.intendedAt })

        // New words are a new send, tapped now.
        vm.onAction(WriteAction.OnTextChange("You made the tea, twice."))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(TAP + 3.minutes, loop.submits.last().intendedAt)
    }

    @Test
    fun theTapIsTakenInWholeMillisecondsSoTheQueuedRetryIsTheSameBody() {
        clock.instant = Instant.parse("2026-09-14T11:00:00.123456789Z")
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(Instant.parse("2026-09-14T11:00:00.123Z"), loop.submits.single().intendedAt)
    }

    @Test
    fun aQueuedSendClosesLikeASavedOneAndTheDraftGoesBecauseTheOutboxHasTheWords() = runTest {
        loop.submitResult = WriteResult.Queued
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertNull(drafts[DraftKind.NewEntry], "a draft left behind would come back as a second entry")
        assertTrue(vm.state.value.submit != WriteSubmit.Failed, "queued is not a failure: no Save failed")
    }

    @Test
    fun tryAgainWithTheSameWordsAndTheSamePromptReplaysTheSameRequest() {
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(
            List(2) { FakeDailyLoop.Submit("You made the tea.", PROMPT.id, "key-1", TAP) },
            loop.submits,
        )
    }

    @Test
    fun dismissingThePromptAfterAFailureMakesTheRetryANewSend() {
        // The same key with a different body is 422 (contract, Idempotency-Key): a retry that
        // drops the prompt is a different body, so it needs its own key. After a lost success
        // that key meets 409 ENTRY_ALREADY_EXISTS, and the loop reloads the day.
        loop.submitResult = WriteResult.Failed
        val vm = viewModel()
        vm.onAction(WriteAction.OnTextChange("You made the tea."))
        vm.onAction(WriteAction.OnSubmit)
        vm.onAction(WriteAction.OnDismissPrompt)
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(
            listOf(
                FakeDailyLoop.Submit("You made the tea.", PROMPT.id, "key-1", TAP),
                FakeDailyLoop.Submit("You made the tea.", null, "key-2", TAP),
            ),
            loop.submits,
        )

        // That new send, retried unchanged, keeps its own key.
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(FakeDailyLoop.Submit("You made the tea.", null, "key-2", TAP), loop.submits.last())
    }

    @Test
    fun tryAgainWithTheSameEditReusesTheKeyAndAChangedEditDoesNot() {
        written("Already written.")
        loop.editResult = WriteResult.Failed
        val vm = viewModel(editing = true)
        vm.onAction(WriteAction.OnTextChange("Already written, and more."))
        vm.onAction(WriteAction.OnSubmit)
        vm.onAction(WriteAction.OnSubmit)
        vm.onAction(WriteAction.OnTextChange("Already written, and more again."))
        vm.onAction(WriteAction.OnSubmit)
        assertEquals(listOf("key-1", "key-1", "key-2"), loop.edits.map { it.key })
    }

    @Test
    fun savingAnEditSendsAnEditNotASecondEntry() = runTest {
        written("Already written.")
        val vm = viewModel(editing = true)
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("Already written, and more."))
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertEquals(listOf(FakeDailyLoop.Edit("Already written, and more.", "key-1")), loop.edits)
        assertTrue(loop.submits.isEmpty())
    }

    // ---- Closing the task (D10) ----

    @Test
    fun closingWithNothingTypedClosesSilently() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnCloseClick)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertFalse(vm.state.value.confirmingDiscard)
    }

    @Test
    fun closingWithOnlySpacesClosesSilently() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("   "))
            vm.onAction(WriteAction.OnCloseClick)
            assertEquals(WriteEvent.Close, awaitItem())
        }
    }

    @Test
    fun closingWithWordsTypedAsksFirstAndDoesNotClose() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnCloseClick)
            assertTrue(vm.state.value.confirmingDiscard)
            expectNoEvents()
        }
    }

    @Test
    fun keepWritingClosesTheQuestionAndKeepsTheWords() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnCloseClick)
            vm.onAction(WriteAction.OnDiscardDismiss)
            assertFalse(vm.state.value.confirmingDiscard)
            assertEquals("You made the tea.", vm.state.value.text)
            expectNoEvents()
        }
        assertTrue(loop.submits.isEmpty())
    }

    @Test
    fun discardClosesAndSendsNothing() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnCloseClick)
            vm.onAction(WriteAction.OnDiscardConfirm)
            assertEquals(WriteEvent.Close, awaitItem())
        }
        assertFalse(vm.state.value.confirmingDiscard)
        assertTrue(loop.submits.isEmpty())
    }

    @Test
    fun aSecondCloseWhileAskingDoesNotCloseUnderTheQuestion() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnCloseClick)
            vm.onAction(WriteAction.OnCloseClick)
            assertTrue(vm.state.value.confirmingDiscard)
            expectNoEvents()
        }
    }

    @Test
    fun editingAndClosingWithNoChangeClosesSilently() = runTest {
        written("Already written.")
        val vm = viewModel(editing = true)
        vm.events.test {
            vm.onAction(WriteAction.OnCloseClick)
            assertEquals(WriteEvent.Close, awaitItem())
        }
    }

    @Test
    fun editingAndClosingWithAChangeAsksFirst() {
        written("Already written.")
        val vm = viewModel(editing = true)
        vm.onAction(WriteAction.OnTextChange("Already written, and more."))
        vm.onAction(WriteAction.OnCloseClick)
        assertTrue(vm.state.value.confirmingDiscard)
    }

    @Test
    fun closingWhileSendingWaitsForTheSend() = runTest {
        val vm = viewModel()
        repeat(3) { vm.onAction(WriteAction.OnDebugCycle) } // 419:193 sending
        vm.events.test {
            vm.onAction(WriteAction.OnCloseClick)
            assertFalse(vm.state.value.confirmingDiscard)
            expectNoEvents()
        }
    }

    @Test
    fun closingAfterAFailedSaveAsksFirstAndOnceQueuedDoesNot() {
        val vm = viewModel()
        repeat(5) { vm.onAction(WriteAction.OnDebugCycle) } // 384:229 save failed: the words are unsent
        vm.onAction(WriteAction.OnCloseClick)
        assertTrue(vm.state.value.confirmingDiscard)
        vm.onAction(WriteAction.OnDiscardDismiss)
        vm.onAction(WriteAction.OnDebugCycle) // queued: the device has the entry
        assertFalse(vm.state.value.wouldLoseWords)
    }

    @Test
    fun aCloseAfterASuccessfulSendDoesNotAskToDiscardTheSavedWords() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("You made the tea."))
            vm.onAction(WriteAction.OnSubmit)
            assertEquals(WriteEvent.Close, awaitItem())
            vm.onAction(WriteAction.OnCloseClick)
            assertFalse(vm.state.value.confirmingDiscard)
            expectNoEvents()
        }
    }

    @Test
    fun editingDownToOnlySpacesClosesSilently() = runTest {
        written("Already written.")
        val vm = viewModel(editing = true)
        vm.events.test {
            vm.onAction(WriteAction.OnTextChange("   "))
            vm.onAction(WriteAction.OnCloseClick)
            assertEquals(WriteEvent.Close, awaitItem())
        }
    }

    @Test
    fun blankSendsNothing() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(WriteAction.OnSubmit)
            expectNoEvents()
        }
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
        assertTrue(loop.submits.isEmpty())
    }

    @Test
    fun editingStartsFromWhatIWroteWithNoPrompt() {
        written("Already written.")
        val vm = viewModel(editing = true)
        assertEquals("Already written.", vm.state.value.text)
        assertTrue(vm.state.value.editing)
        assertNull(vm.state.value.prompt)
    }

    @Test
    fun typingAfterAFailureClearsIt() {
        val vm = viewModel()
        // The debug switch reaches save failed at step 5.
        repeat(5) { vm.onAction(WriteAction.OnDebugCycle) }
        assertEquals(WriteSubmit.Failed, vm.state.value.submit)
        vm.onAction(WriteAction.OnTextChange("${vm.state.value.text} more"))
        assertEquals(WriteSubmit.Idle, vm.state.value.submit)
    }

    @Test
    fun editsWhileSendingAreIgnored() {
        val vm = viewModel()
        repeat(3) { vm.onAction(WriteAction.OnDebugCycle) } // 419:193 sending
        val sending = vm.state.value
        vm.onAction(WriteAction.OnTextChange("changed"))
        assertEquals(sending, vm.state.value)
    }

    @Test
    fun theDebugSwitchVisitsEachDrawnComposerStateAndWraps() {
        val vm = viewModel()
        val seen = (1..7).map {
            vm.onAction(WriteAction.OnDebugCycle)
            vm.state.value
        }
        assertTrue(seen[0].showCounter && !seen[0].overLimit) // 181:43
        assertTrue(seen[1].overLimit) // 181:83
        assertEquals(WriteSubmit.Sending, seen[2].submit) // 419:193
        assertTrue(seen[3].editing) // 419:291
        assertEquals(WriteSubmit.Failed, seen[4].submit) // 384:229
        assertEquals(WriteSubmit.Queued, seen[5].submit)
        assertEquals(WriteState(prompt = FakeCopy.PROMPT), seen[6]) // back to 181:3
    }

    @Test
    fun theDebugFillerNeverPutsThePartnersWordsInMyComposer() {
        val vm = viewModel()
        val partnerWords = FakeCopy.PARTNER_ENTRY.split(". ").map { it.trim().trimEnd('.') }.filter { it.length > 8 }
        repeat(7) {
            vm.onAction(WriteAction.OnDebugCycle)
            val text = vm.state.value.text
            partnerWords.forEach { sentence ->
                assertFalse(text.contains(sentence), "the composer holds the partner's \"$sentence\"")
            }
        }
    }
}

/** The Send tap of every composer test: 12:00 in Lagos on the test day. */
internal val TAP: Instant = Instant.parse("2026-09-14T11:00:00Z")
