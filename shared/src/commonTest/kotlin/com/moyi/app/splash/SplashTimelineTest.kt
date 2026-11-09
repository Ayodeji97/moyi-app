package com.moyi.app.splash

import com.moyi.app.splash.SplashTimeline.CatchUpCapMillis
import com.moyi.app.splash.SplashTimeline.EntranceEndMillis
import com.moyi.app.splash.SplashTimeline.NaturalEndMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SplashTimelineTest {

    @Test
    fun `the first frame is the empty surface of the native launch frame`() {
        val first = SplashTimeline.frameAt(0f)
        assertEquals(0f, first.ringAlpha)
        assertEquals(0f, first.dotAlpha)
        assertEquals(0f, first.wordmarkAlpha)
    }

    @Test
    fun `the ring comes first then the dot then the wordmark`() {
        val ringOnly = SplashTimeline.frameAt(200f)
        assertTrue(ringOnly.ringAlpha > 0f)
        assertEquals(0f, ringOnly.dotAlpha)

        val dotSettling = SplashTimeline.frameAt(400f)
        assertEquals(1f, dotSettling.ringAlpha)
        assertTrue(dotSettling.dotAlpha in 0.01f..0.99f)
        assertTrue(dotSettling.dotLift > 0f, "the dot settles down onto its place")
        assertEquals(0f, dotSettling.wordmarkAlpha)

        val wordmarkArriving = SplashTimeline.frameAt(700f)
        assertEquals(1f, wordmarkArriving.dotAlpha)
        assertEquals(0f, wordmarkArriving.dotLift)
        assertTrue(wordmarkArriving.wordmarkAlpha in 0.01f..0.99f)
    }

    @Test
    fun `the entrance ends on the final frame the one Reduce Motion draws throughout`() {
        assertEquals(SplashTimeline.FinalFrame, SplashTimeline.frameAt(EntranceEndMillis.toFloat()))
        assertEquals(SplashTimeline.FinalFrame, SplashTimeline.frameAt(NaturalEndMillis + 5_000f))
    }

    @Test
    fun `the whole splash runs between one and one and a half seconds`() {
        val total = NaturalEndMillis + SplashTimeline.ExitMillis
        assertTrue(total in 1_000..1_500, "total $total ms")
    }

    @Test
    fun `signed out the splash plays out at its own pace even when the screen is chosen at once`() {
        assertEquals(
            HandOff(entranceMillis = EntranceEndMillis, holdMillis = SplashTimeline.HoldMillis),
            SplashTimeline.handOff(0f, signedIn = false),
        )
        assertEquals(HandOff(entranceMillis = 0, holdMillis = 20), SplashTimeline.handOff(1_100f, signedIn = false))
    }

    @Test
    fun `signed in at the very start the entrance is squeezed into the cap and nothing is held`() {
        assertEquals(HandOff(entranceMillis = CatchUpCapMillis, holdMillis = 0), SplashTimeline.handOff(0f, signedIn = true))
        assertEquals(HandOff(entranceMillis = CatchUpCapMillis, holdMillis = 0), SplashTimeline.handOff(60f, signedIn = true))
    }

    @Test
    fun `signed in when the rest of the entrance fits it plays at its own pace and the remainder is held`() {
        // 620: 200 of entrance left, cap 400, so 200 at normal speed and 200 held.
        assertEquals(HandOff(entranceMillis = 200, holdMillis = 200), SplashTimeline.handOff(620f, signedIn = true))
    }

    @Test
    fun `signed in late enough it leaves at its natural end`() {
        val handOff = SplashTimeline.handOff(770f, signedIn = true)
        assertEquals(HandOff(entranceMillis = 50, holdMillis = 300), handOff)
        assertEquals(NaturalEndMillis - 770, handOff.totalMillis)
    }

    @Test
    fun `ready after the natural end it leaves at once either way`() {
        listOf(true, false).forEach { signedIn ->
            assertEquals(HandOff(0, 0), SplashTimeline.handOff(NaturalEndMillis.toFloat(), signedIn))
            assertEquals(HandOff(0, 0), SplashTimeline.handOff(NaturalEndMillis + 3_000f, signedIn))
        }
    }

    @Test
    fun `a signed-in start never waits more than the cap after the restore`() {
        (0..NaturalEndMillis + 100 step 10).forEach { position ->
            val handOff = SplashTimeline.handOff(position.toFloat(), signedIn = true)
            assertTrue(handOff.totalMillis <= CatchUpCapMillis, "at $position: $handOff")
            assertTrue(handOff.entranceMillis >= 0 && handOff.holdMillis >= 0, "at $position: $handOff")
        }
    }

    @Test
    fun `a signed-out start never outlasts the natural end`() {
        (0..NaturalEndMillis + 100 step 10).forEach { position ->
            val handOff = SplashTimeline.handOff(position.toFloat(), signedIn = false)
            assertTrue(position + handOff.totalMillis <= maxOf(position, NaturalEndMillis), "at $position: $handOff")
        }
    }

    @Test
    fun `a stalled frame moves the timeline by one capped step so no part is skipped`() {
        // 250 ms without a frame (the first screen composing): the timeline moves 34, not 250.
        assertEquals(234f, SplashTimeline.advance(200f, frameMillis = 250f, speed = 1f, target = 820f))
        assertEquals(216f, SplashTimeline.advance(200f, frameMillis = 16f, speed = 1f, target = 820f))
    }

    @Test
    fun `a faster hand-off moves further per frame and stops at its target`() {
        assertEquals(232f, SplashTimeline.advance(200f, frameMillis = 16f, speed = 2f, target = 820f))
        assertEquals(820f, SplashTimeline.advance(810f, frameMillis = 16f, speed = 1f, target = 820f))
        assertEquals(200f, SplashTimeline.advance(200f, frameMillis = -5f, speed = 1f, target = 820f))
    }
}
