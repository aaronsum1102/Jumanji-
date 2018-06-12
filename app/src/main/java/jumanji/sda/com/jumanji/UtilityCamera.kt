package jumanji.sda.com.jumanji

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

object UtilityCamera {
    const val PERMISSIONS_TO_READ_EXTERNAL_STORAGE = 100
    private val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun checkPermission(fragment: Fragment? = null, context: Context, callback: OnPermissionGrantedCallback) {
        if (ContextCompat.checkSelfPermission(context, permissions[0])
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, permissions[0])) {
                createPermissionRationaleDialog(fragment, context)
            } else {
                requestForPermission(fragment, context)
            }
        } else {
            callback.actionWithPermission(context)
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
                    displayToastForNoPermission(fragment)
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

    private fun displayToastForNoPermission(fragment: Fragment?) {
        fragment?.let {
            fragment.view?.let {
                Snackbar.make(it,
                        fragment.requireContext().getString(R.string.snackbarNoStoragePermission),
                        Snackbar.LENGTH_LONG)
                        .setAction(fragment.requireContext().getString(R.string.snackbarNoStorageAction)) {
                            requestForPermission(fragment, fragment.requireContext())
                        }
                        .show()
            }
        }
    }
}
