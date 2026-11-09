package com.moyi.app.feature.today.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.component.InlineError
import com.moyi.app.designsystem.component.MoyiText
import com.moyi.app.designsystem.component.ReactionChoices
import com.moyi.app.designsystem.component.TextTone
import com.moyi.app.domain.today.Reaction
import com.moyi.app.feature.today.resources.Res
import com.moyi.app.feature.today.resources.reaction_chosen
import com.moyi.app.feature.today.resources.reaction_from_partner_heart
import com.moyi.app.feature.today.resources.reaction_from_partner_me_too
import com.moyi.app.feature.today.resources.reaction_from_partner_moved
import com.moyi.app.feature.today.resources.reaction_from_partner_smile
import com.moyi.app.feature.today.resources.reaction_from_partner_thank_you
import com.moyi.app.feature.today.resources.reaction_heart
import com.moyi.app.feature.today.resources.reaction_me_too
import com.moyi.app.feature.today.resources.reaction_moved
import com.moyi.app.feature.today.resources.reaction_prompt
import com.moyi.app.feature.today.resources.reaction_smile
import com.moyi.app.feature.today.resources.reaction_take_back
import com.moyi.app.feature.today.resources.reaction_thank_you
import com.moyi.app.feature.today.resources.today_action_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a tap on [tapped] asks for, when [current] is what I have left: the same one takes
 * it back (null), any other replaces it. One reaction per member per entry (ADR-0037).
 */
internal fun nextReaction(current: Reaction?, tapped: Reaction): Reaction? = if (current == tapped) null else tapped

/**
 * Under the partner's revealed entry, wherever it is shown (Today, the Archive day view):
 * a line saying what to do, then the interim [ReactionChoices] with [mine] selected, then
 * the Generic failure line when the last tap did not go through ([failed]; the choice is
 * already back as it was). Interim: docs/missing-components.md §45.
 */
@Composable
internal fun ReactionControl(
    partnerName: String,
    mine: Reaction?,
    failed: Boolean,
    onChoose: (Reaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = Reaction.entries
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MoyiTheme.spacing.sm)) {
        MoyiText(
            text = if (mine == null) stringResource(Res.string.reaction_prompt, partnerName) else stringResource(Res.string.reaction_chosen),
            tone = TextTone.Muted,
        )
        ReactionChoices(
            labels = all.map { stringResource(it.word) },
            selected = mine?.let { all.indexOf(it) },
            onChoose = { onChoose(all[it]) },
            takeBackLabel = stringResource(Res.string.reaction_take_back),
        )
        if (failed) InlineError(stringResource(Res.string.today_action_failed))
    }
}

/** Under my live entry: the reaction the partner left on it, as one plain sentence. Nothing when none. */
@Composable
internal fun PartnerReaction(reaction: Reaction?, partnerName: String, modifier: Modifier = Modifier) {
    if (reaction == null) return
    MoyiText(text = stringResource(reaction.fromPartner, partnerName), tone = TextTone.Muted, modifier = modifier)
}

private val Reaction.word: StringResource
    get() = when (this) {
        Reaction.HEART -> Res.string.reaction_heart
        Reaction.THANK_YOU -> Res.string.reaction_thank_you
        Reaction.SMILE -> Res.string.reaction_smile
        Reaction.MOVED -> Res.string.reaction_moved
        Reaction.ME_TOO -> Res.string.reaction_me_too
    }

private val Reaction.fromPartner: StringResource
    get() = when (this) {
        Reaction.HEART -> Res.string.reaction_from_partner_heart
        Reaction.THANK_YOU -> Res.string.reaction_from_partner_thank_you
        Reaction.SMILE -> Res.string.reaction_from_partner_smile
        Reaction.MOVED -> Res.string.reaction_from_partner_moved
        Reaction.ME_TOO -> Res.string.reaction_from_partner_me_too
    }
