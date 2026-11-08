package com.moyi.app.designsystem.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@Composable
actual fun rememberPhotoPicker(onPick: (PhotoPick) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPick by rememberUpdatedState(onPick)
    // The camera writes to this file; kept across a configuration change or process death
    // while the camera app is in front, so its result can still be read.
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun prepare(read: () -> PickedPhoto, afterwards: () -> Unit = {}) {
        scope.launch {
            val photo = withContext(Dispatchers.IO) {
                try {
                    read()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } catch (_: OutOfMemoryError) {
                    null
                } finally {
                    afterwards()
                }
            }
            currentOnPick(if (photo != null) PhotoPick.Picked(photo) else PhotoPick.Unreadable)
        }
    }

    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) prepare({ AndroidPhotoPreparation.prepare(context, uri) })
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile?.let(::File)
        cameraFile = null
        when {
            file == null -> Unit
            // The full-size original is deleted as soon as it is read: only the prepared copy is kept, in memory.
            taken -> prepare({ AndroidPhotoPreparation.prepare(context, Uri.fromFile(file)) }, afterwards = { file.delete() })
            else -> file.delete()
        }
    }

    return remember(context) {
        object : PhotoPicker {
            override val canTakePhoto: Boolean =
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

            override fun chooseFromLibrary() {
                library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }

            override fun takePhoto() {
                val file = AndroidPhotoPreparation.newCameraFile(context)
                cameraFile = file.absolutePath
                val uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    cameraFile = null
                    file.delete()
                    currentOnPick(PhotoPick.Unreadable)
                }
            }
        }
    }
}

/** The FileProvider's authority after the application id; androidApp's manifest declares it. */
private const val AUTHORITY_SUFFIX = ".photos"

internal object AndroidPhotoPreparation {
    /** Under `cacheDir/camera`, the one directory `res/xml/photo_paths.xml` shares; emptied of any leftover first. */
    fun newCameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    /**
     * Reads [uri] three times, each time from the start: its size alone (no pixels), its
     * orientation, then its pixels at the reduced size [PhotoPreparation.sampleSize]
     * picks, so a 48 MP photo never sits whole in memory. Then scaled to fit, turned
     * upright and encoded as JPEG.
     */
    fun prepare(context: Context, uri: Uri): PickedPhoto {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
        val orientation = resolver.openInputStream(uri).use { stream ->
            stream?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } ?: ExifInterface.ORIENTATION_NORMAL
        val options = BitmapFactory.Options().apply { inSampleSize = PhotoPreparation.sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = checkNotNull(resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }) { "not decoded" }
        val (width, height) = PhotoPreparation.fittedSize(decoded.width, decoded.height)
        val scaled = if (width == decoded.width && height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true).also { if (it !== decoded) decoded.recycle() }
        }
        val upright = scaled.upright(orientation)
        try {
            val bytes = encode(upright)
            return PickedPhoto(bytes, upright.width, upright.height)
        } finally {
            upright.recycle()
        }
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        var bytes = ByteArray(0)
        for (quality in PhotoPreparation.QUALITIES) {
            bytes = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "not encoded" }
                out.toByteArray()
            }
            if (bytes.size <= PhotoPreparation.MAX_BYTES) return bytes
        }
        error("too large")
    }

    /** The pixels turned as the EXIF orientation says, since the JPEG written keeps no EXIF. */
    private fun Bitmap.upright(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
            else -> return this
        }
        val turned = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (turned !== this) recycle()
        return turned
    }
}
