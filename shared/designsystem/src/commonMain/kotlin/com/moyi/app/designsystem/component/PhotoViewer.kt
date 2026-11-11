package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.platform.ProtectFromCapture
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_close
import com.moyi.app.designsystem.resources.ds_entry_photo
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/** The viewer's root, for tests. */
const val PHOTO_VIEWER_TAG = "photo_viewer"

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §70, answering §42): an entry's photo
 * full screen, opened by a tap on it in an [EntryCard].
 *
 * - On `surface`, edge to edge: a 48 row at the top holds the close target at its start,
 *   then the whole photo, fitted (never cropped), centred in what is left. The close
 *   target never sits on the photo, so it keeps its contrast on any picture.
 * - Closes with the ✕ ("Close"), with Android's Back, and nothing else (no tap outside:
 *   there is no outside).
 * - Pinch to zoom, from 1× to 4×, and a drag to move the zoomed photo. A new viewer opens
 *   at 1×.
 * - [protectFromCapture] for a partner's photo (R3): on Android the viewer's own window
 *   carries `FLAG_SECURE` (a dialog is a window of its own, so the card's flag on the
 *   activity would not cover it); on iOS the dialog is drawn in the app's window, so the
 *   app-switcher cover ([ProtectFromCapture]) covers it as it covers the card.
 *
 * It is a dialog rather than a destination, so the screen under it (Today, the Archive)
 * keeps its scroll and its state, and nothing in navigation changes.
 */
@Composable
fun PhotoViewer(url: String, onClose: () -> Unit, protectFromCapture: Boolean) {
    Dialog(onDismissRequest = onClose, properties = photoViewerProperties(protectFromCapture)) {
        if (protectFromCapture) ProtectFromCapture()
        val colors = MoyiTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding()
                .testTag(PHOTO_VIEWER_TAG),
        ) {
            Box(
                modifier = Modifier
                    .size(MoyiDimens.TouchTarget)
                    .clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MoyiIcons.Close,
                    contentDescription = stringResource(Res.string.ds_close),
                    tint = colors.ink,
                    modifier = Modifier.size(MoyiDimens.IconGlyph),
                )
            }
            ZoomablePhoto(url, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun ZoomablePhoto(url: String, modifier: Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = url,
        contentDescription = stringResource(Res.string.ds_entry_photo),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    // At 1x the photo fits and has nowhere to move.
                    offset = if (scale == MIN_ZOOM) Offset.Zero else offset + pan
                }
            }
            // Read in the draw phase: a pinch redraws, it does not recompose.
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

/**
 * Full screen, closed by Back and never by a tap outside. Android: edge to edge, and
 * `FLAG_SECURE` on the dialog's own window when [protectFromCapture]. iOS: drawn under
 * the system bars, the content keeping clear of them by its own padding.
 */
internal expect fun photoViewerProperties(protectFromCapture: Boolean): DialogProperties
