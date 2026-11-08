package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * A photo picked or taken, already prepared for upload on the phone: decoded at a reduced
 * size, scaled so its longer edge is at most [PhotoPreparation.MAX_EDGE], turned upright,
 * and re-encoded as JPEG. Re-encoding writes none of the original's metadata, so the
 * place it was taken, the camera and the time never leave the phone (the server strips
 * them too, T-08; this is the second lock).
 *
 * Treated as immutable: nothing writes to [bytes] after it is made. Never printed.
 */
@Immutable
class PickedPhoto(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
) {
    val contentType: String get() = PhotoPreparation.CONTENT_TYPE

    override fun toString(): String = "PickedPhoto(${width}x$height, ${bytes.size} bytes)"
}

/** What a pick ended with. A pick the person cancelled ends with nothing at all. */
sealed interface PhotoPick {
    data class Picked(val photo: PickedPhoto) : PhotoPick

    /** The photo could not be read or prepared (a format the phone cannot decode, a file that went away). */
    data object Unreadable : PhotoPick
}

/**
 * The system's own photo picker and camera.
 *
 * - **Android**: the Photo Picker (`PickVisualMedia`), which needs no storage permission
 *   (it falls back to the document picker below Android 11), and the camera app through
 *   `TakePicture`, which needs no camera permission because Moyi does not declare one.
 * - **iOS**: `PHPickerViewController`, which needs no photo library permission (it runs
 *   out of process and hands over only what was chosen), and `UIImagePickerController`
 *   for the camera, which asks with `NSCameraUsageDescription`.
 */
interface PhotoPicker {
    /** False where there is no camera (the iOS simulator, a tablet without one): only the library is offered. */
    val canTakePhoto: Boolean

    fun chooseFromLibrary()

    fun takePhoto()
}

/** [onPick] is called on the main thread, once per pick that was not cancelled. */
@Composable
expect fun rememberPhotoPicker(onPick: (PhotoPick) -> Unit): PhotoPicker

/**
 * The limits a photo is prepared to, both platforms (the arithmetic is here so it is
 * tested once). The backend takes JPEG up to 10 MiB (`MediaKind.IMAGE`) and keeps at most
 * a 2048 px variant (`ImageVariant.FULL`), so nothing larger than 2048 px is worth sending.
 */
object PhotoPreparation {
    const val CONTENT_TYPE = "image/jpeg"

    /** The backend's largest kept variant (`ImageVariant.FULL`, 2048 px on the longer edge). */
    const val MAX_EDGE = 2048

    /** The backend's upload cap for an image (`MediaKind.IMAGE`: 10 MiB). */
    const val MAX_BYTES = 10L * 1024 * 1024

    /** JPEG qualities tried in order until the photo fits [MAX_BYTES]; at 2048 px the first nearly always does. */
    val QUALITIES = listOf(85, 75, 60, 45)

    /**
     * [width] × [height] scaled to fit within [maxEdge] on the longer edge, keeping the
     * shape; never scaled up, and never below 1 px on either edge.
     */
    fun fittedSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0) { "no size" }
        val longer = maxOf(width, height)
        if (longer <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longer
        return maxOf(1, (width * scale).roundToIntSafely()) to maxOf(1, (height * scale).roundToIntSafely())
    }

    /**
     * The largest power of two to decode at (Android's `inSampleSize`) that still leaves
     * the longer edge at least [maxEdge]: a 48 MP photo (8064 × 6048) decodes at 1/2
     * (4032 × 3024, 49 MB as ARGB) instead of whole (195 MB), then scales to 2048.
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        require(width > 0 && height > 0) { "no size" }
        val longer = maxOf(width, height)
        var sample = 1
        while (longer / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    private fun Double.roundToIntSafely(): Int = kotlin.math.round(this).toInt()
}
