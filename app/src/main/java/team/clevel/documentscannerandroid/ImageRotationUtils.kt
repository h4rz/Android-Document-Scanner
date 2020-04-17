package team.clevel.documentscannerandroid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Harsh Rajgor on 16/04/20.
 */

/**
 * This method is responsible for solving the rotation issue if exist. Also scale the images to
 * 1024x1024 resolution
 *
 * @param context       The current context
 * @param selectedImage The Image URI
 * @return Bitmap image results
 * @throws IOException
 */
@Throws(IOException::class)
fun handleSamplingAndRotationBitmap(context: Context, selectedImage: Uri): Bitmap {
    val MAX_HEIGHT = 1024
    val MAX_WIDTH = 1024

    // First decode with inJustDecodeBounds=true to check dimensions
    val options: BitmapFactory.Options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    var imageStream: InputStream = context.contentResolver.openInputStream(selectedImage)!!
    BitmapFactory.decodeStream(imageStream, null, options)
    imageStream.close()

    // Calculate inSampleSize
    options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH, MAX_HEIGHT)

    // Decode bitmap with inSampleSize set
    options.inJustDecodeBounds = false
    imageStream = context.getContentResolver().openInputStream(selectedImage)!!
    var img: Bitmap = BitmapFactory.decodeStream(imageStream, null, options)!!
    img = rotateImageIfRequired(img, selectedImage)
    return img
}

/**
 * Calculate an inSampleSize for use in a [BitmapFactory.Options] object when decoding
 * bitmaps using the decode* methods from [BitmapFactory]. This implementation calculates
 * the closest inSampleSize that will result in the final decoded bitmap having a width and
 * height equal to or larger than the requested width and height. This implementation does not
 * ensure a power of 2 is returned for inSampleSize which can be faster when decoding but
 * results in a larger bitmap which isn't as useful for caching purposes.
 *
 * @param options   An options object with out* params already populated (run through a decode*
 * method with inJustDecodeBounds==true
 * @param reqWidth  The requested width of the resulting bitmap
 * @param reqHeight The requested height of the resulting bitmap
 * @return The value to be used for inSampleSize
 */
private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int, reqHeight: Int
): Int {
    // Raw height and width of image
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {

        // Calculate ratios of height and width to requested height and width
        val heightRatio =
            Math.round(height.toFloat() / reqHeight.toFloat())
        val widthRatio =
            Math.round(width.toFloat() / reqWidth.toFloat())

        // Choose the smallest ratio as inSampleSize value, this will guarantee a final image
        // with both dimensions larger than or equal to the requested height and width.
        inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio

        // This offers some additional logic in case the image has a strange
        // aspect ratio. For example, a panorama may have a much larger
        // width than height. In these cases the total pixels might still
        // end up being too large to fit comfortably in memory, so we should
        // be more aggressive with sample down the image (=larger inSampleSize).
        val totalPixels = width * height.toFloat()

        // Anything more than 2x the requested pixels we'll sample down further
        val totalReqPixelsCap = reqWidth * reqHeight * 2.toFloat()
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++
        }
    }
    return inSampleSize
}

/**
 * Rotate an image if required.
 *
 * @param img           The image bitmap
 * @param selectedImage Image URI
 * @return The resulted Bitmap after manipulation
 */
@Throws(IOException::class)
private fun rotateImageIfRequired(
    img: Bitmap,
    selectedImage: Uri
): Bitmap {
    val ei = ExifInterface(selectedImage.path)
    val orientation: Int =
        ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
        else -> img
    }
}

private fun rotateImage(
    img: Bitmap,
    degree: Int
): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degree.toFloat())
    val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
    img.recycle()
    return rotatedImg
}

@SuppressLint("SimpleDateFormat")
@Throws(IOException::class)
fun createImageFile(): File {
    // Create an image file name
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    val storageDir = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES
    )
    val image = File.createTempFile(
        imageFileName, // prefix
        ".jpg", // suffix
        storageDir      // directory
    )
    Log.i("********image", image.absolutePath)
    return image
}
