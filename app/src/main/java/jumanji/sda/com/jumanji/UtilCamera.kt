package jumanji.sda.com.jumanji

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

data class PhotoExif(val orientation: String?,
                     val lat: Double?,
                     val long: Double?)

object UtilCamera {
    const val PERMISSIONS_TO_READ_EXTERNAL_STORAGE = 100
    const val SELECT_IMAGE_CODE = 200
    const val USE_CAMERA_CODE = 201
    private val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun checkPermissionBeforeAction(fragment: Fragment? = null, context: Context) {
        if (ContextCompat.checkSelfPermission(context, permissions[0])
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, permissions[0])) {
                createPermissionRationaleDialog(fragment, context)
            } else {
                requestForPermission(fragment, context)
            }
        } else {
            chooseFromGallery(fragment, context)
        }
    }

    private fun createPermissionRationaleDialog(fragment: Fragment?, context: Context) {
        AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.permissionTitleStorage))
                .setMessage(context.getString(R.string.permissionContentStorage))
                .setPositiveButton(android.R.string.ok, { dialog, _ ->
                    dialog.dismiss()
                    requestForPermission(fragment, context)
                })
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                    displayMessageForNoPermission(fragment, context)
                }
                .create()
                .show()
    }

    private fun requestForPermission(fragment: Fragment?, context: Context) {
        if (fragment != null) {
            fragment.requestPermissions(
                    permissions,
                    PERMISSIONS_TO_READ_EXTERNAL_STORAGE)
        } else {
            ActivityCompat.requestPermissions(context as Activity,
                    permissions,
                    PERMISSIONS_TO_READ_EXTERNAL_STORAGE)
        }
    }

    fun displayMessageForNoPermission(fragment: Fragment?, context: Context) {
        fragment?.let {
            fragment.view?.let { view ->
                Snackbar.make(view,
                        context.getString(R.string.snackbarNoStoragePermission),
                        Snackbar.LENGTH_LONG)
                        .setAction(fragment.requireContext().getString(R.string.snackbarNoStorageAction)) {
                            requestForPermission(fragment, context)
                        }
                        .show()
            }
        }
    }

    fun chooseFromGallery(fragment: Fragment?, context: Context) {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_PICK
        val chooser = Intent.createChooser(intent, "Select File")
        if (chooser.resolveActivity(context.packageManager) != null) {
            if (fragment == null) {
                (context as Activity).startActivityForResult(chooser, SELECT_IMAGE_CODE)
            } else {
                fragment.startActivityForResult(chooser, SELECT_IMAGE_CODE)
            }
        }
    }

    fun getMetadataFromPhoto(uri: Uri, context: Context): PhotoExif {
        val inputStream = context.contentResolver.openInputStream(uri)
        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION)
        val latitude = exif.latLong?.get(0)
        val longitude = exif.latLong?.get(1)
        return PhotoExif(orientation, latitude, longitude)
    }

    fun warningForNoMetadataInPhoto(context: Context) {
        Toast.makeText(context,
                context.getString(R.string.noPositionMetaData),
                Toast.LENGTH_SHORT)
                .show()
    }

    fun resizeImage(uri: Uri, context: Context): File? {
        val bitmap = getBitmap(uri, context)
        val originalRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetHeight = 600
        val targetWidth = (originalRatio * targetHeight).toInt()
        val newImage = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false)
        val outputStream = ByteArrayOutputStream()
        newImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val file = createImageFIle(context)
        file?.writeBytes(outputStream.toByteArray())
        outputStream.flush()
        outputStream.close()
        return file
    }

    private fun getBitmap(uri: Uri, context: Context): Bitmap {
        val cursor = context.contentResolver.query(uri,
                arrayOf(MediaStore.Images.Media.DATA),
                null, null, null)
        cursor.moveToFirst()
        return if (cursor.columnCount != 0) {
            val path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            cursor.close()
            BitmapFactory.decodeFile(path)
        } else {
            cursor.close()
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        }
    }

    private fun createImageFIle(context: Context): File? {
        try {
            val file = File(context.filesDir, "images")
            if (!file.exists()) {
                file.mkdir()
            }
            return File.createTempFile("tmp", ".jpg", file)
        } catch (exception: IOException) {
        }
        return null
    }

    fun getUriForFile(file: File, context: Context): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun loadPhotoIntoView(uri: Uri, orientation: Int, @LayoutRes imageView: ImageView) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                Picasso.get().load(uri).rotate(90f).into(imageView)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                Picasso.get().load(uri).rotate(180f).into(imageView)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                Picasso.get().load(uri).rotate(270f).into(imageView)
            }
            else -> {
                Picasso.get().load(uri).into(imageView)
            }
        }
    }

    fun useCamera(context: Context, fragment: Fragment?, activityCompat: Activity?): File? {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(context.packageManager) != null) {
            val photoFile = createImageFIle(context)
            photoFile?.let {
                val uri = getUriForFile(photoFile, context)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                intent.putExtra("return-data", true)
                if (fragment != null) {
                    fragment.startActivityForResult(intent, UtilCamera.USE_CAMERA_CODE)
                } else {
                    activityCompat?.startActivityForResult(intent, UtilCamera.USE_CAMERA_CODE)
                }
                return photoFile
            }
        }
        return null
    }
}
