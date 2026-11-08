package com.moyi.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.ProtectFromCapture
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_compose_attached_photo
import com.moyi.app.designsystem.resources.ds_compose_remove_photo
import com.moyi.app.designsystem.resources.ds_entry_photo
import com.moyi.app.designsystem.resources.ds_entry_photo_not_shared
import com.moyi.app.designsystem.resources.ds_entry_photo_on_its_way
import com.moyi.app.designsystem.resources.ds_entry_photo_sensitive
import com.moyi.app.designsystem.theme.MoyiDimens
import org.jetbrains.compose.resources.stringResource

/** How an [EntryCard]'s photo is drawn for this reader (from the server's `EntryImageResponse`). */
enum class EntryPhotoLook {
    /** The photo. */
    Shown,

    /** Passed moderation as sensitive (Q16): covered until tapped. */
    Covered,

    /** Not shown to this reader yet, for a reason never given: one muted line, no image. */
    OnItsWay,

    /** Its author's only: held back from the other member (Q10). The photo if [EntryPhoto.url] is set, and the line. */
    NotShared,
}

/**
 * A photo on an [EntryCard]. [url] is a signed GET and so a credential: it is never
 * printed. [key] (the media id) keys what a reader uncovered, so it survives a reload
 * whose URL changed on the hour.
 */
@Immutable
data class EntryPhoto(
    val key: String,
    val url: String?,
    val width: Int?,
    val height: Int?,
    val look: EntryPhotoLook,
) {
    override fun toString(): String = "EntryPhoto(key=$key, url=${url?.let { "(redacted)" }}, ${width}x$height, look=$look)"
}

/**
 * **Interim, not drawn** (docs/missing-components.md §40): the photo inside an EntryCard,
 * under the words, the card's full inner width, its own shape kept (clamped so a
 * panorama or a tall screenshot stays readable), on `surface` until it loads: no
 * spinner, no shimmer. The card's corner radius. Nothing opens it larger: no viewer is
 * drawn (§42).
 */
@Composable
fun EntryPhotoView(photo: EntryPhoto, protectFromCapture: Boolean = false) {
    // R3, for a partner's photo drawn outside an EntryCard (the Archive list's day card).
    if (protectFromCapture) ProtectFromCapture()
    val colors = MoyiTheme.colors
    val shapes = MoyiTheme.shapes
    var uncovered by rememberSaveable(photo.key) { mutableStateOf(false) }
    val frame = Modifier
        .fillMaxWidth()
        .aspectRatio(photo.aspectRatio())
        .clip(shapes.radius)
        .background(colors.surface)
    when (photo.look) {
        EntryPhotoLook.Shown -> PhotoImage(photo.url, frame)
        EntryPhotoLook.Covered -> if (uncovered) {
            PhotoImage(photo.url, frame)
        } else {
            Box(
                modifier = frame.clickable(role = Role.Button) { uncovered = true },
                contentAlignment = Alignment.Center,
            ) {
                MoyiText(
                    text = stringResource(Res.string.ds_entry_photo_sensitive),
                    tone = TextTone.Muted,
                    modifier = Modifier.padding(MoyiDimens.EntryCard.LabelGap),
                )
            }
        }
        EntryPhotoLook.OnItsWay -> MoyiText(text = stringResource(Res.string.ds_entry_photo_on_its_way), tone = TextTone.Muted)
        EntryPhotoLook.NotShared -> Column {
            if (photo.url != null) {
                PhotoImage(photo.url, frame)
                Spacer(Modifier.height(MoyiTheme.spacing.sm))
            }
            MoyiText(text = stringResource(Res.string.ds_entry_photo_not_shared), tone = TextTone.Muted)
        }
    }
}

@Composable
private fun PhotoImage(url: String?, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = stringResource(Res.string.ds_entry_photo),
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** The photo's own shape, held between 3:4 (tall) and 2:1 (wide); 4:3 when the server did not say. */
internal fun EntryPhoto.aspectRatio(): Float {
    val w = width ?: return DEFAULT_RATIO
    val h = height ?: return DEFAULT_RATIO
    if (w <= 0 || h <= 0) return DEFAULT_RATIO
    return (w.toFloat() / h).coerceIn(TALLEST, WIDEST)
}

private const val DEFAULT_RATIO = 4f / 3f
private const val TALLEST = 3f / 4f
private const val WIDEST = 2f

/**
 * **Interim, not drawn** (docs/missing-components.md §41): the photo being added, inside
 * the composer's box above its toolbar, a small preview at [AttachedPhotoHeight] with a
 * 48 target beside it that removes it. Picking another photo replaces it.
 */
@Composable
fun AttachedPhoto(photo: PickedPhoto, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MoyiTheme.colors
    val ratio = (photo.width.toFloat() / photo.height).coerceIn(TALLEST, WIDEST)
    Row(modifier = modifier.padding(MoyiDimens.ComposeField.Wrap), verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = photo.bytes,
            contentDescription = stringResource(Res.string.ds_compose_attached_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(AttachedPhotoHeight)
                .aspectRatio(ratio)
                .clip(MoyiTheme.shapes.radius)
                .background(colors.surface),
        )
        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .size(MoyiDimens.ComposeField.IconTarget)
                    .clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MoyiIcons.Close,
                    contentDescription = stringResource(Res.string.ds_compose_remove_photo),
                    tint = colors.ink,
                    modifier = Modifier.size(MoyiDimens.IconGlyph),
                )
            }
        }
    }
}

/** Two 48 targets tall: enough to recognise the photo, small enough to leave the words their room. */
private val AttachedPhotoHeight = 96.dp
