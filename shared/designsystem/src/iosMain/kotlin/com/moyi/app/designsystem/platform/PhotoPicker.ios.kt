@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.moyi.app.designsystem.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberPhotoPicker(onPick: (PhotoPick) -> Unit): PhotoPicker {
    val host = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val currentOnPick by rememberUpdatedState(onPick)
    return remember(host) { IosPhotoPicker(host, scope) { currentOnPick(it) } }
}

/**
 * Holds the two delegates strongly: UIKit keeps only weak references to them, so a
 * delegate made inline would be gone before the person had chosen.
 */
private class IosPhotoPicker(
    private val host: UIViewController,
    private val scope: CoroutineScope,
    private val onPick: (PhotoPick) -> Unit,
) : PhotoPicker {
    override val canTakePhoto: Boolean =
        UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

    private fun deliver(read: () -> PickedPhoto?) {
        scope.launch {
            val photo = withContext(Dispatchers.Default) { runCatching(read).getOrNull() }
            onPick(if (photo != null) PhotoPick.Picked(photo) else PhotoPick.Unreadable)
        }
    }

    private val libraryDelegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
            // On a background queue of the picker's; the bytes are prepared off the main thread too.
            result.itemProvider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ ->
                deliver { data?.let(IosPhotoPreparation::prepare) }
            }
        }
    }

    private val cameraDelegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
            // The camera's UIImage, written once at full quality so ImageIO can read it at the reduced size.
            deliver { UIImageJPEGRepresentation(image, 1.0)?.let(IosPhotoPreparation::prepare) }
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, completion = null)
        }
    }

    override fun chooseFromLibrary() {
        val configuration = PHPickerConfiguration().apply {
            filter = PHPickerFilter.imagesFilter
            selectionLimit = 1
        }
        val picker = PHPickerViewController(configuration = configuration)
        picker.delegate = libraryDelegate
        host.topmost().presentViewController(picker, animated = true, completion = null)
    }

    override fun takePhoto() {
        if (!canTakePhoto) return
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = cameraDelegate
        host.topmost().presentViewController(picker, animated = true, completion = null)
    }
}

private const val IMAGE_TYPE = "public.image"

private fun UIViewController.topmost(): UIViewController {
    var top = this
    while (true) top = top.presentedViewController ?: return top
}

internal object IosPhotoPreparation {
    /**
     * ImageIO's thumbnail: decodes [data] straight to at most [PhotoPreparation.MAX_EDGE]
     * on the longer edge, upright (`WithTransform` applies the EXIF orientation), without
     * ever holding the full-size pixels: a 48 MP HEIC never becomes a 190 MB bitmap.
     * Then JPEG, without the original's metadata.
     */
    fun prepare(data: NSData): PickedPhoto? {
        val cfData = CFBridgingRetain(data) as CFDataRef?
        val source = CGImageSourceCreateWithData(cfData, null)
        cfData?.let { CFRelease(it) }
        source ?: return null
        val options = CFDictionaryCreateMutable(null, 3, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        val maxEdge = memScoped {
            val edge = alloc<platform.posix.int32_tVar>().apply { value = PhotoPreparation.MAX_EDGE }
            CFNumberCreate(null, kCFNumberIntType, edge.ptr)
        }
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
        CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxEdge)
        val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
        CFRelease(maxEdge)
        CFRelease(options)
        CFRelease(source)
        thumbnail ?: return null
        try {
            val width = CGImageGetWidth(thumbnail).toInt()
            val height = CGImageGetHeight(thumbnail).toInt()
            if (width <= 0 || height <= 0) return null
            val image = UIImage.imageWithCGImage(thumbnail)
            for (quality in PhotoPreparation.QUALITIES) {
                val jpeg = UIImageJPEGRepresentation(image, quality / 100.0) ?: return null
                if (jpeg.length.toLong() <= PhotoPreparation.MAX_BYTES) return PickedPhoto(jpeg.toByteArray(), width, height)
            }
            return null
        } finally {
            CGImageRelease(thumbnail)
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes
    }
}
