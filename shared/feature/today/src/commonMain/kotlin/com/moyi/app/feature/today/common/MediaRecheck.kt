package com.moyi.app.feature.today.common

import com.moyi.app.designsystem.component.EntryPhoto
import com.moyi.app.designsystem.component.EntryPhotoLook
import com.moyi.app.designsystem.component.VoiceNote
import com.moyi.app.designsystem.component.VoiceNoteLook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long to wait before each re-read of a day that shows a photo or voice note "on its
 * way": soon at first, because moderation usually clears a photo in seconds, then once a
 * minute. About half an hour in all ([recheckWhileOnItsWay] then stops until the screen
 * shows again): the partner is shown PROCESSING for a photo held back for review as well,
 * forever and on purpose (EntryImageResponse), so one that never clears must not keep
 * the phone asking all day.
 */
internal val MediaRecheckDelays: List<Duration> =
    listOf(5.seconds, 10.seconds, 20.seconds, 30.seconds) + List(29) { 60.seconds }

/**
 * Re-reads the day while it shows something on its way, so the photo or voice note
 * appears the moment the server has cleared it, with nothing to tap and nothing new
 * drawn (no spinner, no copy: the "on its way" line simply becomes the photo).
 *
 * [onItsWay] is whether the shown day holds media on its way now; [reload] reads the day
 * once through the screen's own load, which publishes into the same state [onItsWay]
 * reads, so there is no second copy of the day. Each wait is the next of [delays]; a
 * wait ends early, and the schedule starts over, when nothing is on its way any more.
 * [reload] is always awaited, so two re-reads never overlap. Returns once [delays] has run
 * out; the caller cancels it when the screen stops showing.
 */
internal suspend fun recheckWhileOnItsWay(
    onItsWay: Flow<Boolean>,
    reload: suspend () -> Unit,
    delays: List<Duration> = MediaRecheckDelays,
) {
    var step = 0
    while (step < delays.size) {
        onItsWay.first { it }
        // The wait, cut short if the day stops holding anything on its way (another load cleared it).
        val cleared = withTimeoutOrNull(delays[step]) { onItsWay.first { !it } } != null
        if (cleared) {
            step = 0
            continue
        }
        reload()
        step++
    }
}

/** True when this photo is drawn as "A photo is on its way." (`EntryMediaStatus.Processing`). */
internal fun EntryPhoto?.isOnItsWay(): Boolean = this?.look == EntryPhotoLook.OnItsWay

/** True when this voice note is drawn as on its way (`EntryMediaStatus.Processing`). */
internal fun VoiceNote?.isOnItsWay(): Boolean = this?.look == VoiceNoteLook.OnItsWay

/** Any photo or voice note this day shows, mine or the partner's, is on its way. */
internal fun DayView.holdsMediaOnItsWay(): Boolean =
    myPhoto.isOnItsWay() || partnerPhoto.isOnItsWay() || myVoice.isOnItsWay() || partnerVoice.isOnItsWay()
