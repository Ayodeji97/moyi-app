package com.moyi.app.designsystem.platform

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.moyi.app.designsystem.icon.MoyiIcons
import kotlinx.coroutines.launch

internal actual val PlatformBackIcon: ImageVector get() = MoyiIcons.ChevronLeft

internal actual fun platformPressIndication(color: Color): Indication = DimIndication

internal actual val DismissKeyboardOnTapAndDrag: Boolean = true

/**
 * **Interpreted**: neither Figma nor tokens.md states a pressed value. 0.4 sits
 * between UIKit's text-button highlight (about 0.2) and PrimaryAction's Sending
 * alpha (0.6), so a press never looks like Sending.
 */
private const val PressedAlpha = 0.4f

/** The dim fades back over this; a quick tap still shows it. */
private const val ReleaseMillis = 150

/**
 * The iOS press: everything drawn after the `clickable` (its background,
 * border and content, when they come after it in the chain) at [PressedAlpha]
 * while pressed, fading back on release. No ripple, no colour.
 *
 * An [IndicationNodeFactory]: one node per clickable, no composition of its
 * own. A singleton, so `equals` is identity and clickables never recreate it.
 */
private object DimIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = DimNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0x4D6F7969
}

private class DimNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(1f)
    private val layerPaint = Paint()

    override fun onAttach() {
        coroutineScope.launch {
            // A node detached mid-press and attached again must not stay dimmed:
            // the press count below starts from zero, so the alpha does too.
            alpha.snapTo(1f)
            var pressed = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                // Animatable's mutex cancels whichever change is still running.
                launch {
                    if (pressed > 0) alpha.snapTo(PressedAlpha) else alpha.animateTo(1f, tween(ReleaseMillis))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        // Read in draw, so a change only redraws: no recomposition, no relayout.
        val current = alpha.value
        if (current >= 1f) {
            drawContent()
            return
        }
        layerPaint.alpha = current
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), layerPaint) }
        drawContent()
        drawIntoCanvas { it.restore() }
    }
}
