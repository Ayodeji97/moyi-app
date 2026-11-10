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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.moyi.app.designsystem.MoyiTheme
import com.moyi.app.designsystem.icon.MoyiIcons
import com.moyi.app.designsystem.platform.PickedPhoto
import com.moyi.app.designsystem.platform.ProtectFromCapture
import com.moyi.app.designsystem.resources.Res
import com.moyi.app.designsystem.resources.ds_compose_attached_photo
import com.moyi.app.designsystem.resources.ds_compose_keep_sent_photo
import com.moyi.app.designsystem.resources.ds_compose_remove_photo
import com.moyi.app.designsystem.resources.ds_compose_replace_photo
import com.moyi.app.designsystem.resources.ds_compose_sent_photo
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
 *
 * [removeRestoresSent]: in Edit, where taking this new photo away puts the entry's own back,
 * the target is named "Keep the photo you had" instead of "Remove the photo" (§60).
 *
 * [compact] (PROPOSED, §66: the keyboard is up): a [MoyiDimens.ComposeField.CompactThumbnail]
 * square instead of the preview, the same 48 target beside it, so the words keep their room.
 */
@Composable
fun AttachedPhoto(
    photo: PickedPhoto,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    removeRestoresSent: Boolean = false,
    compact: Boolean = false,
) {
    val removeLabel = stringResource(if (removeRestoresSent) Res.string.ds_compose_keep_sent_photo else Res.string.ds_compose_remove_photo)
    PhotoRow(
        model = photo.bytes,
        ratio = (photo.width.toFloat() / photo.height).coerceIn(TALLEST, WIDEST),
        description = stringResource(Res.string.ds_compose_attached_photo),
        modifier = modifier,
        compact = compact,
    ) {
        if (onRemove != null) PhotoIconTarget(MoyiIcons.Close, removeLabel, onRemove)
    }
}

/**
 * **PROPOSED, not drawn** (docs/missing-components.md §60): in Edit, the photo already on
 * the entry, drawn as [AttachedPhoto] is, with a 48 target beside it that picks a new one
 * in its place ([onReplace]; null while saving). It has **no remove**: the contract cannot
 * take a photo off an entry (docs/backend-contract-gaps.md row 41). A photo the server has
 * not finished with (no [EntryPhoto.url]) is the card's muted line instead of the image.
 * [compact] as [AttachedPhoto]'s (§66).
 */
@Composable
fun SentPhoto(photo: EntryPhoto, onReplace: (() -> Unit)?, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (photo.url == null) {
        AttachmentFrame(modifier) {
            Row(modifier = Modifier.padding(MoyiDimens.ComposeField.Wrap), verticalAlignment = Alignment.CenterVertically) {
                MoyiText(text = stringResource(Res.string.ds_entry_photo_on_its_way), tone = TextTone.Muted, modifier = Modifier.weight(1f))
                if (onReplace != null) PhotoIconTarget(MoyiIcons.Image, stringResource(Res.string.ds_compose_replace_photo), onReplace)
            }
        }
        return
    }
    PhotoRow(
        model = photo.url,
        ratio = photo.aspectRatio(),
        description = stringResource(Res.string.ds_compose_sent_photo),
        modifier = modifier,
        compact = compact,
    ) {
        if (onReplace != null) PhotoIconTarget(MoyiIcons.Image, stringResource(Res.string.ds_compose_replace_photo), onReplace)
    }
}

/**
 * The small preview and, beside it, the [trailing] 48 target: shared by [AttachedPhoto] and [SentPhoto].
 * [compact]: a square thumbnail centred in one 48 line, starting where the words start.
 */
@Composable
private fun PhotoRow(
    model: Any?,
    ratio: Float,
    description: String,
    modifier: Modifier,
    compact: Boolean,
    trailing: @Composable () -> Unit,
) {
    // In its own frame, so the control beside it is plainly the photo's (§66).
    AttachmentFrame(modifier) {
        PhotoRowContent(model, ratio, description, compact, trailing)
    }
}

@Composable
private fun PhotoRowContent(
    model: Any?,
    ratio: Float,
    description: String,
    compact: Boolean,
    trailing: @Composable () -> Unit,
) {
    if (compact) {
        Row(
            modifier = Modifier
                .heightIn(min = MoyiDimens.ComposeField.IconTarget)
                .padding(start = MoyiDimens.ComposeField.Wrap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = model,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(MoyiDimens.ComposeField.CompactThumbnail)
                    .clip(MoyiTheme.shapes.radius)
                    .background(MoyiTheme.colors.surface),
            )
            trailing()
        }
        return
    }
    Row(modifier = Modifier.padding(MoyiDimens.ComposeField.Wrap), verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = model,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(AttachedPhotoHeight)
                .aspectRatio(ratio)
                .clip(MoyiTheme.shapes.radius)
                .background(MoyiTheme.colors.surface),
        )
        trailing()
    }
}

@Composable
private fun PhotoIconTarget(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(MoyiDimens.ComposeField.IconTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MoyiTheme.colors.ink,
            modifier = Modifier.size(MoyiDimens.IconGlyph),
        )
    }
}

/** Two 48 targets tall: enough to recognise the photo, small enough to leave the words their room. */
private val AttachedPhotoHeight = 96.dp
