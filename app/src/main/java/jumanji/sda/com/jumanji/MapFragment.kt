package jumanji.sda.com.jumanji

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_map.*
import java.io.File

interface SetOnPopUpWindowAdapter {
    fun displayPopUpWindow(marker: Marker)
}

class MapFragment : Fragment(), OnMapReadyCallback, SetOnPopUpWindowAdapter {
    companion object {
        private const val LAST_KNOWN_ZOOM = "last_known_zoom"
        private const val LAST_KNOWN_LONGITUDE = "last_known_longitude"
        private const val LAST_KNOWN_LATITUDE = "last_known_latitude"
        private const val CAMERA_PREFERENCE = "camera_preference"
        private const val RESTORE_STATE_FLAG = "restore_state_flag"
        private const val LOCATION_REQUEST_CODE = 300
        private const val REQUEST_SETTING_CHECK = 30
    }

    private lateinit var mapPreference: CameraStateManager
    private lateinit var map: GoogleMap
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var pinViewModel: PinViewModel
    private lateinit var profileViewModel: ProfileViewModel
    private lateinit var statisticViewModel: StatisticViewModel
    private var currentLocation = LatLng(LocationViewModel.DEFAULT_LATITUDE, LocationViewModel.DEFAULT_LONGITUDE)
    private var userChoosenTask: String = ""

    private var currentView: LatLngBounds? = null
    private lateinit var mapAdapter: GoogleMapAdapter
    private var email: String = ""
    private var username: String = ""
    private var photo: File? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView.onCreate(savedInstanceState)
        profileViewModel = ViewModelProviders.of(activity!!)[ProfileViewModel::class.java]
        pinViewModel = ViewModelProviders.of(activity!!)[PinViewModel::class.java]

        profileViewModel.userInfo?.observe(this, Observer {
            if (it?.email != null) email = it.email
            if (it?.userName != null) username = it.userName
        })

        profileViewModel.reportedPins.observe(this, Observer {
            totalNoOfTrashLocationText.text = it
        })

        profileViewModel.cleanedPins.observe(this, Observer {
            totalNoOfTrashLocationClearedText.text = it
        })

        statisticViewModel = ViewModelProviders.of(activity!!)[StatisticViewModel::class.java]

        mapPreference = CameraStateManager()
        mapAdapter = GoogleMapAdapter()

        mapView.getMapAsync(this)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()

        refreshFab.setOnClickListener {
            if (currentView != null && mapAdapter.map != null) {
                Snackbar.make(it, "loading locations...", Snackbar.LENGTH_SHORT).show()
                map.clear()
                pinViewModel.loadPinData()
                mapAdapter.bindMarkers()
            }
        }
        reportFab.setOnClickListener {
            createDialogForAction()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        profileViewModel.updateUserStatistics(username)
    }

    override fun onPause() {
        super.onPause()
        if (this::mapPreference.isInitialized) {
            mapPreference.saveMapCameraState(true)
        }
        mapView.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (this::mapPreference.isInitialized) {
            mapPreference.saveMapCameraState(false)
        }
        mapView?.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        mapView?.onLowMemory()
        super.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.isIndoorEnabled = false

        locationViewModel = ViewModelProviders.of(activity!!)[LocationViewModel::class.java]
        locationViewModel.currentLocation.observe(activity!!, Observer {
            if (it != null) {
                currentLocation = it
            }
        })

        enableMyLocationLayer()
        checkUserLocationSetting()
        if (mapPreference.shouldRestoreState()) {
            val cameraState = mapPreference.getCameraState()
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraState))
        } else {
            locationViewModel.moveToLastKnowLocation(map)
        }

        map.setOnMyLocationButtonClickListener {
            locationViewModel.moveToLastKnowLocation(map)
            true
        }

        pinViewModel.map = map
        pinViewModel.loadPinData()

        mapAdapter.map = map

        pinViewModel.trashMarkers.observe(this, Observer {
            it?.let {
                mapAdapter.trashLocationMarkers = it
                mapAdapter.bindMarkers()
            }
        })

        pinViewModel.trashFreeMarkers.observe(this, Observer {
            it?.let {
                mapAdapter.trashFreeMarkers = it
                mapAdapter.bindMarkers()
            }
        })

        map.setOnMarkerClickListener { marker ->

            val point = map.projection.toScreenLocation(marker.position)
            val widthPixels = context!!.resources.displayMetrics.widthPixels
            val heightPixels = context!!.resources.displayMetrics.heightPixels
            val xTargetPosition = widthPixels / 2
            val yTargetPosition = (heightPixels / 2) + 300
            val xOffset = (point.x - xTargetPosition).toFloat()
            val yOffset = (point.y - yTargetPosition).toFloat()

            map.animateCamera(CameraUpdateFactory.scrollBy(xOffset, yOffset),
                    100,
                    object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            displayPopUpWindow(marker)
                        }

                        override fun onCancel() {}
                    })
            true
        }

        map.setOnCameraIdleListener {
            mapAdapter.bindMarkers()
        }
    }

    override fun displayPopUpWindow(marker: Marker) {
        val popUpWindowView = layoutInflater.inflate(R.layout.fragment_info_window, null)
        val popupWindow = PopupWindow(popUpWindowView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true)
        val imageHolder = popUpWindowView.findViewById<ImageView>(R.id.imageHolder)
        val url = (marker.tag as PinData).imageURL
        if (url.isNotEmpty()) {
            Picasso.get()
                    .load(url)
                    .placeholder(R.drawable.logo1)
                    .fit()
                    .rotate(90f)
                    .centerInside()
                    .into(imageHolder)
        }
        val clearButton = popUpWindowView.findViewById<Button>(R.id.clearButton)
        if ((marker.tag as PinData).isTrash) {
            clearButton.visibility = View.VISIBLE
        } else {
            clearButton.visibility = View.INVISIBLE
        }
        clearButton.setOnClickListener {
            val pinData = marker.tag as PinData
            pinViewModel.reportPointAsClean(pinData)
            profileViewModel.updateUserCleanedPinNumber(username)
            pinViewModel.loadPinData()
            profileViewModel.updateUserStatistics(username)
            popupWindow.dismiss()
            statisticViewModel.updateCommunityStatistics(StatisticRepository.TOTAL_CLEANED_PINS)
        }
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, -100)
    }

    private fun checkUserLocationSetting() {
        val context = this@MapFragment.context ?: return
        locationViewModel.initiateUserSettingCheck(context)
                .addOnCompleteListener { task ->
                    try {
                        task.getResult(ApiException::class.java)
                    } catch (e: ApiException) {
                        if (e.statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                            try {
                                (e as? ResolvableApiException)
                                        ?.startResolutionForResult(this@MapFragment.activity,
                                                REQUEST_SETTING_CHECK)
                            } catch (error: IntentSender.SendIntentException) {
                                Log.d("ERROR", "${error.message}")
                            }
                        }
                    }
                }
    }

    private fun enableMyLocationLayer() {
        val permission = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION
                , Manifest.permission.ACCESS_FINE_LOCATION)
        if (ActivityCompat.checkSelfPermission(context!!, permission[0]) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context!!, permission[1]) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity as Activity, permission[1])) {
                android.app.AlertDialog.Builder(context)
                        .setTitle("Permission Request")
                        .setMessage("I need the location service to know where the trash is.")
                        .setCancelable(true)
                        .setNegativeButton("OK", { dialog, _ ->
                            run {
                                dialog.dismiss()
                                requestPermissions(permission, LOCATION_REQUEST_CODE)
                            }
                        })
                        .create()
                        .show()
            } else {
                requestPermissions(permission, LOCATION_REQUEST_CODE)
            }
        } else {
            if (this::map.isInitialized) {
                map.isMyLocationEnabled = true
            }
            locationViewModel.moveToLastKnowLocation(map)
        }
    }

    private fun createDialogForAction() {
        val context = this@MapFragment.context
        if (context != null) {
            val items = arrayOf("Take Photo", "Choose from Library", "Cancel")
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Report")
            builder.setItems(items, { dialog, item ->
                when {
                    items[item] == "Take Photo" -> {
                        userChoosenTask = "Take Photo"
                        locationViewModel.startLocationUpdates(context)
                        photo = UtilCamera.useCamera(context, this@MapFragment)
                    }
                    items[item] == "Choose from Library" -> {
                        userChoosenTask = "Choose from Library"
                        UtilCamera.checkPermissionBeforeAction(this, context)
                    }
                    items[item] == "Cancel" -> dialog.dismiss()
                }
            })
            builder.show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                    enableMyLocationLayer()
                } else {
                    Toast.makeText(context,
                            "Please enable permission to access your device location, so that I know where the trash is at.",
                            Toast.LENGTH_LONG)
                            .show()
                }
            }

            UtilCamera.PERMISSIONS_TO_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    context?.let {
                        UtilCamera.chooseFromGallery(this@MapFragment, it)
                    }
                } else {
                    this@MapFragment.context?.let {
                        UtilCamera.displayMessageForNoPermission(this@MapFragment, it)
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val photoRepository = PhotoRepository(email)
        val context = this.context
        when (requestCode) {
            UtilCamera.USE_CAMERA_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    context?.let {
                        photo?.let {
                            val photoUri = UtilCamera.getUriForFile(it, context)
                            // TODO compare gps and exif location data, currently using photo location.
                            val photoExif = UtilCamera.getMetadataFromPhoto(photoUri, context)
                            uploadPhoto(photoUri, photoExif, photoRepository)
                            locationViewModel.stopLocationUpdates()
                        }
                    }
                }
            }

            UtilCamera.SELECT_IMAGE_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val uri = data.data
                    val photoExif = UtilCamera.getMetadataFromPhoto(uri, this.requireContext())
                    uploadPhoto(uri, photoExif, photoRepository)
                }
            }
        }
    }

    private fun uploadPhoto(uri: Uri, photoExif: PhotoExif, photoRepository: PhotoRepository) {
        val (orientation, lat, long) = photoExif
        if (orientation != null && lat != null && long != null) {
            context?.let { context ->
                val resizeImageFile = UtilCamera.resizeImage(uri, context)
                resizeImageFile?.let {
                    val uriForUpload = UtilCamera.getUriForFile(it, context)
                    notifyUploadingOfPhoto(context)
                    photoRepository.storePhotoToDatabase(uriForUpload, activity)
                            .continueWith { uploadTask ->
                                uploadTask.result.storage.downloadUrl
                                        .addOnSuccessListener { downloadUrl ->
                                            resizeImageFile.delete()
                                            photo?.let { it.delete() }
                                            updatePinData(downloadUrl, photoExif)
                                            profileViewModel.updateUserPinNumber(username)
                                            pinViewModel.loadPinData()
                                            profileViewModel.updateUserStatistics(username)
                                            statisticViewModel.updateCommunityStatistics(StatisticRepository.TOTAL_REPORTED_PINS)
                                            notifySuccessUploadOfPhoto(context)
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(context,
                                                    "Something went wrong when uploading photo.",
                                                    Toast.LENGTH_SHORT).show()
                                            Log.e("UploadPhoto", "error upload photo.${it.message}")
                                        }
                            }
                }
            }
        } else {
            this@MapFragment.context?.let {
                UtilCamera.warningForNoMetadataInPhoto(it)
            }
        }
    }

    private fun notifyUploadingOfPhoto(context: Context) {
        Toast.makeText(context,
                context.getString(R.string.uploadingPhoto),
                Toast.LENGTH_SHORT)
                .show()
    }

    private fun notifySuccessUploadOfPhoto(context: Context) {
        Toast.makeText(context,
                context.getString(R.string.successUploadTrashPhoto),
                Toast.LENGTH_SHORT)
                .show()
    }

    private fun updatePinData(downloadUrl: Uri, photoExif: PhotoExif) {
        if (photoExif.lat != null &&
                photoExif.long != null &&
                photoExif.orientation != null) {
            pinViewModel.reportPointForTrash(
                    PinDataInfo(photoExif.long.toFloat(),
                            photoExif.lat.toFloat(),
                            photoExif.orientation,
                            downloadUrl.toString(),
                            username,
                            true))
        }
    }

    class GoogleMapAdapter {
        var map: GoogleMap? = null
        var trashLocationMarkers: List<Marker> = listOf()
        var trashFreeMarkers: List<Marker> = listOf()

        fun bindMarkers() {

            getCurrentView()?.let { bounds ->
                trashLocationMarkers.filter { bounds.contains(it.position) }
                        .forEach { it.isVisible = true }
                trashLocationMarkers.filterNot { bounds.contains(it.position) }
                        .forEach { it.isVisible = false }

                trashFreeMarkers.filter { bounds.contains(it.position) }
                        .forEach { it.isVisible = true }
                trashFreeMarkers.filterNot { bounds.contains(it.position) }
                        .forEach { it.isVisible = false }
            }
        }

        private fun getCurrentView(): LatLngBounds? {
            map?.let { map ->
                return map.projection.visibleRegion.latLngBounds
            }
            return null
        }
    }

    inner class CameraStateManager {
        private val mapCameraPreferences = this@MapFragment.context!!.getSharedPreferences(
                CAMERA_PREFERENCE,
                Context.MODE_PRIVATE)

        fun saveMapCameraState(restoreFlag: Boolean) {
            val cameraPosition = map.cameraPosition
            val latitude = cameraPosition.target.latitude.toFloat()
            val longitude = cameraPosition.target.longitude.toFloat()
            val zoom = cameraPosition.zoom
            val editor = mapCameraPreferences.edit()
            editor.putFloat(LAST_KNOWN_LATITUDE, latitude)
            editor.putFloat(LAST_KNOWN_LONGITUDE, longitude)
            editor.putFloat(LAST_KNOWN_ZOOM, zoom)
            editor.putBoolean(RESTORE_STATE_FLAG, restoreFlag)
            editor.apply()
        }

        fun getCameraState(): CameraPosition {
            val latitude = mapCameraPreferences.getFloat(LAST_KNOWN_LATITUDE,
                    LocationViewModel.DEFAULT_LATITUDE.toFloat())
                    .toDouble()
            val longitude = mapCameraPreferences.getFloat(LAST_KNOWN_LONGITUDE,
                    LocationViewModel.DEFAULT_LONGITUDE.toFloat())
                    .toDouble()
            val zoom = mapCameraPreferences.getFloat(LAST_KNOWN_ZOOM,
                    LocationViewModel.DEFAULT_ZOOM_LEVEL)
            return CameraPosition(LatLng(latitude, longitude), zoom, 0.0f, 0.0f)
        }

        fun shouldRestoreState(): Boolean {
            return mapCameraPreferences.getBoolean(RESTORE_STATE_FLAG, false)
        }
    }
}