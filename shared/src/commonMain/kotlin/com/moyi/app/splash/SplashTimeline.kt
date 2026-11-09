package com.moyi.app.splash

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import com.moyi.app.designsystem.theme.MoyiMotion

/**
 * The launch splash's choreography, as pure numbers (owner request 2026-11-09: "the two
 * big circles of the logo, one coming first, the other on it, then the text; not too
 * much"). Times are milliseconds on the splash's own timeline, which starts at the first
 * Compose frame and never jumps more than [MaxFrameStepMillis] in one frame ([advance]).
 * Every duration and curve is a `tokens.md` §3a motion token:
 *
 * | Part | When | How |
 * |---|---|---|
 * | Ring (the o) | 0 to 300 | fades in and grows from 92 % (`motion.push`: 300 ms, `FastOutSlowIn`) |
 * | Dot (the dot below) | 220 to 520 | fades in and settles down onto its place under the ring (same token), overlapping the ring's last 80 ms |
 * | Wordmark | 610 to 820 | fades in (`motion.fadeIn`: 210 ms after a 90 ms delay, `LinearOutSlowIn`) |
 * | Hold | 820 to 1120 | the complete lockup, still |
 * | Exit | at the hand-off ([handOff]) | cross-fades to the first screen (`motion.crossFade`, 200 ms) |
 *
 * No bounce, no overshoot (tokens.md §3a): each part arrives once and stops.
 */
internal object SplashTimeline {
    const val RingStartMillis = 0
    const val RingMillis = MoyiMotion.PushMillis

    /** The dot starts while the ring is still settling, so the two read as one gesture. */
    const val DotStartMillis = 220
    const val DotMillis = MoyiMotion.PushMillis

    const val WordmarkStartMillis = DotStartMillis + DotMillis + MoyiMotion.FadeInDelayMillis
    const val WordmarkMillis = MoyiMotion.FadeInMillis

    /** Everything has arrived. */
    const val EntranceEndMillis = WordmarkStartMillis + WordmarkMillis

    /** How long the finished lockup stays when nothing is waiting on it. */
    const val HoldMillis = 300

    /** The entrance plus the hold: the splash's natural length. */
    const val NaturalEndMillis = EntranceEndMillis + HoldMillis

    /**
     * The longest the splash may stay after a signed-in session is restored ([handOff]).
     * Not a motion token; a bound on waiting.
     */
    const val CatchUpCapMillis = 400

    /** The splash's exit, a cross-fade to the first screen. */
    const val ExitMillis = MoyiMotion.CrossFadeMillis

    /** The ring starts at this scale and grows to 1. */
    const val RingStartScale = 0.92f

    /** The dot starts this fraction of its own diameter above its place, then settles down. */
    const val DotDropFraction = 0.35f

    /**
     * The most the timeline moves in one frame. A cold start's first frames are slow (the
     * first screen composes underneath at the same moment), and a timeline driven by the
     * wall clock would skip a whole part in one stalled frame: the dot would appear rather
     * than settle. Capped, a stall pauses the motion instead; about two frames at 60 Hz.
     */
    const val MaxFrameStepMillis = 34f

    /**
     * The timeline after one frame: [frameMillis] since the last frame, at [speed] (1 is
     * the written pace), never past [target].
     */
    fun advance(position: Float, frameMillis: Float, speed: Float, target: Float): Float =
        (position + frameMillis.coerceIn(0f, MaxFrameStepMillis) * speed).coerceAtMost(target)

    private val Arrive: Easing = FastOutSlowInEasing
    private val FadeIn: Easing = LinearOutSlowInEasing

    /** What the splash draws at [timeMillis]. Before 0 nothing shows; from [EntranceEndMillis] on, the final frame. */
    fun frameAt(timeMillis: Float): SplashFrame {
        val ring = Arrive.transform(fraction(timeMillis, RingStartMillis, RingMillis))
        val dot = Arrive.transform(fraction(timeMillis, DotStartMillis, DotMillis))
        val wordmark = FadeIn.transform(fraction(timeMillis, WordmarkStartMillis, WordmarkMillis))
        return SplashFrame(
            ringAlpha = ring,
            ringScale = RingStartScale + (1f - RingStartScale) * ring,
            dotAlpha = dot,
            dotLift = DotDropFraction * (1f - dot),
            wordmarkAlpha = wordmark,
        )
    }

    /** The finished lockup: what Reduce Motion shows from the first frame. */
    val FinalFrame: SplashFrame = SplashFrame(ringAlpha = 1f, ringScale = 1f, dotAlpha = 1f, dotLift = 0f, wordmarkAlpha = 1f)

    /**
     * The hand-off rule: what the splash does once the first screen is chosen, when its
     * timeline stands at [positionMillis].
     *
     * - **Signed out** (the promise screen, which needs nothing from the network): the
     *   splash plays out at its written pace and leaves at the natural end, or at once if
     *   that has passed. This is the person's first sight of the app, and a fast start
     *   chooses its screen in the first few frames, so anything quicker would never be seen.
     * - **Signed in** (the daily start, whose screen is already loading underneath): the
     *   splash leaves at most [CatchUpCapMillis] after the session is restored. If the rest
     *   of the entrance fits in that time it plays at its own pace and the remainder is
     *   held; if not, it plays faster so that it still finishes, and nothing is held. Ready
     *   at or after the natural end, it leaves at once.
     *
     * Reduce Motion follows the same rule: the timeline still counts, only the frame drawn is
     * the finished lockup throughout, so it neither flashes nor stays longer.
     *
     * The exit cross-fade ([ExitMillis]) follows; the first screen shows through it.
     */
    fun handOff(positionMillis: Float, signedIn: Boolean): HandOff {
        val position = positionMillis.coerceIn(0f, NaturalEndMillis.toFloat())
        val untilNaturalEnd = NaturalEndMillis - position
        val leaveIn = if (signedIn) untilNaturalEnd.coerceAtMost(CatchUpCapMillis.toFloat()) else untilNaturalEnd
        val entranceLeft = (EntranceEndMillis - position).coerceAtLeast(0f)
        val entrance = entranceLeft.coerceAtMost(leaveIn)
        return HandOff(entranceMillis = ceil(entrance), holdMillis = ceil(leaveIn - entrance))
    }

    private fun fraction(time: Float, start: Int, duration: Int): Float =
        ((time - start) / duration).coerceIn(0f, 1f)

    private fun ceil(value: Float): Int = kotlin.math.ceil(value).toInt()
}

/**
 * One frame of the splash. [dotLift] is how far above its place the dot is, as a
 * fraction of its diameter.
 */
internal data class SplashFrame(
    val ringAlpha: Float,
    val ringScale: Float,
    val dotAlpha: Float,
    val dotLift: Float,
    val wordmarkAlpha: Float,
)

/**
 * What happens after the first screen is ready: the rest of the entrance plays over
 * [entranceMillis] (faster than written when that is shorter than what was left), then
 * the finished lockup stays [holdMillis], then the exit.
 */
internal data class HandOff(val entranceMillis: Int, val holdMillis: Int) {
    val totalMillis: Int get() = entranceMillis + holdMillis
}
