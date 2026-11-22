@file:OptIn(ExperimentalTestApi::class)

package com.moyi.app.feature.today.archive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.moyi.app.designsystem.MoyiTheme
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Remember when screen as drawn (iOS, where Compose UI tests run here): the fourth chip,
 * Ask disabled until there is a question and reading Asking while one is answered, the
 * answer's count announced from its label, the year on a day outside this year, and the two
 * failure lines.
 */
class RememberScreenTest {
    private fun day(date: LocalDate) = ArchiveDayUi(date, "Pepper soup.", false, "e-$date", "Thank you.", canDelete = true, partnerEntryId = "t-$date")

    private val asking = ArchiveState(load = ArchiveLoad.Ready, filter = ArchiveFilter.Remember, showFilters = true, showSearch = true, showRemember = true)

    @Test
    fun askIsDisabledWithoutAQuestionAndTapsSendIt() = runComposeUiTest {
        val actions = mutableListOf<ArchiveAction>()
        var state by mutableStateOf(asking)
        setContent { MoyiTheme(darkTheme = true) { ArchiveScreen(state, onAction = { actions += it }) } }

        onNodeWithText("Remember when").assertExists()
        onNodeWithText("Ask about a moment").assertExists()
        onNodeWithText("Ask").assertIsNotEnabled()

        state = asking.copy(question = "soup", canAsk = true)
        onNodeWithText("Ask").assertIsEnabled().performClick()
        assertEquals(true, ArchiveAction.OnAskClick in actions)
    }

    @Test
    fun whileAskingTheActionSaysSo() = runComposeUiTest {
        setContent { MoyiTheme(darkTheme = false) { ArchiveScreen(asking.copy(question = "soup", asking = true), onAction = {}) } }
        onNodeWithText("Asking").assertIsNotEnabled()
    }

    @Test
    fun theAnswerAnnouncesItsCountAndNamesAnOlderYear() = runComposeUiTest {
        val results = listOf(
            RememberResult(day(LocalDate(2026, 11, 4)), withYear = false),
            RememberResult(day(LocalDate(2025, 9, 26)), withYear = true),
        )
        setContent { MoyiTheme(darkTheme = true) { ArchiveScreen(asking.copy(question = "soup", canAsk = true, results = results, partnerName = "Tunde"), onAction = {}) } }

        onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("2 moments, closest first"))).assertExists()
        // The date label is drawn for the eye; the row says the same date, once, to a screen reader.
        onNode(hasContentDescription("Friday, 26 September 2025", substring = true)).assertExists()
        onNode(hasContentDescription("Wednesday, 4 November.", substring = true)).assertExists()
    }

    @Test
    fun nothingCloseIsItsOwnBlock() = runComposeUiTest {
        setContent { MoyiTheme(darkTheme = true) { ArchiveScreen(asking.copy(question = "Accra", canAsk = true, results = emptyList()), onAction = {}) } }
        onNodeWithText("Nothing close to that yet.").assertExists()
    }

    @Test
    fun theTwoFailuresSayDifferentThings() = runComposeUiTest {
        var state by mutableStateOf(asking.copy(question = "soup", rememberFailure = RememberFailure.RateLimited))
        setContent { MoyiTheme(darkTheme = true) { ArchiveScreen(state, onAction = {}) } }
        onNodeWithText("This is paused for a short while. Try again in a few minutes.").assertExists()
        onNodeWithText("Ask").assertIsNotEnabled()

        state = asking.copy(question = "soup", canAsk = true, rememberFailure = RememberFailure.Failed)
        onNodeWithText("That didn't work. Nothing was lost. Try again in a moment.").assertExists()
        onNodeWithText("Try again").assertExists()
    }
}
