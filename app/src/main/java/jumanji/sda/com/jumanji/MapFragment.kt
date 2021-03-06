package jumanji.sda.com.jumanji

import android.Manifest
import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
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
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_map.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

interface SetOnPopUpWindowAdapter {
    fun displayPopUpWindow(marker: Marker)
}

interface OnUrlAvailableCallback {
    fun storeDataToFirebase(uri: Uri)
}

interface OnPermissionGrantedCallback {
    fun actionWithPermission(context: Context)
}

class MapFragment : Fragment(), PhotoListener, OnMapReadyCallback, SetOnPopUpWindowAdapter, OnUrlAvailableCallback, OnPermissionGrantedCallback {
    companion object {
        private const val LAST_KNOWN_ZOOM = "last_known_zoom"
        private const val LAST_KNOWN_LONGITUDE = "last_known_longitude"
        private const val LAST_KNOWN_LATITUDE = "last_known_latitude"
        private const val CAMERA_PREFERENCE = "camera_preference"
        private const val RESTORE_STATE_FLAG = "restore_state_flag"
        private const val LOCATION_REQUEST_CODE = 300
        private const val REQUEST_CAMERA_CODE = 100
        private const val SELECT_FILE_CODE = 200
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
            selectImage()
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

    override fun actionWithPermission(context: Context) {
        when (userChoosenTask) {
            "Take Photo" -> {
                locationViewModel.startLocationUpdates(context)
                cameraIntent()
            }

            "Choose from Library" -> {
                galleryIntent()
            }
        }
    }

    override fun selectImage() {
        val context = this@MapFragment.context
        if (context != null) {
            val items = arrayOf("Take Photo", "Choose from Library", "Cancel")
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Report")
            builder.setItems(items, { dialog, item ->
                when {
                    items[item] == "Take Photo" -> {
                        userChoosenTask = "Take Photo"
                        Utility.checkPermission(this, context, this)
                    }

                    items[item] == "Choose from Library" -> {
                        userChoosenTask = "Choose from Library"
                        Utility.checkPermission(this, context, this)
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

            Utility.MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    context?.let {
                        actionWithPermission(it)
                    }
                } else {
                    Toast.makeText(context,
                            "Please give me the permission to access your storage, so that I can reporting trash.",
                            Toast.LENGTH_LONG)
                            .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val photoRepository = PhotoRepository(email)

        when (requestCode) {
            REQUEST_CAMERA_CODE -> {
                locationViewModel.stopLocationUpdates()
                if (resultCode == Activity.RESULT_OK) {
                    val photoFile = File(mCurrentPhotoPath)
                    // Continue only if the File was successfully created
                    val photoURI: Uri = FileProvider.getUriForFile(context!!,
                            "com.android.fileprovider",
                            photoFile)
                    data?.data = photoURI
                    photoRepository.storePhotoToDatabase(photoURI, activity, this, true)
                }
            }

            SELECT_FILE_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    photoRepository.storePhotoToDatabase(data.data, activity, this, true)
                    val position = getLatLngFromPhoto(data)
                    if (position?.latitude == 0.0 && position?.longitude == 0.0) {
                        Toast.makeText(this@MapFragment.context,
                                "No position available from photo",
                                Toast.LENGTH_SHORT).show()
                    } else {
                        Log.d("TAG", "lat lng of photo : $position")
                    }
                }
            }
        }
    }

    override fun storeDataToFirebase(uri: Uri) {
        pinViewModel.reportPointForTrash(PinDataInfo(
                currentLocation.longitude.toFloat(),
                currentLocation.latitude.toFloat(),
                uri.toString(),
                username,
                true))
        profileViewModel.updateUserPinNumber(username)
        pinViewModel.loadPinData()
        profileViewModel.updateUserStatistics(username)
        statisticViewModel.updateCommunityStatistics(StatisticRepository.TOTAL_REPORTED_PINS)
    }


    private var mCurrentPhotoPath: String = ""


    private fun cameraIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(context?.packageManager) != null) {
            try {
                val photoFile: File = createImageFile()
                // Continue only if the File was successfully created
                val photoURI: Uri = FileProvider.getUriForFile(context!!,
                        "com.android.fileprovider",
                        photoFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                intent.putExtra("return-data", true)
                startActivityForResult(intent, REQUEST_CAMERA_CODE)
            } catch (ex: IOException) {
                // Error occurred while creating the File
                Log.e(ex.message, ex.toString())
            }
        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName: String = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.MEDIA_SHARED)

        if (!storageDir.isDirectory)
            storageDir.mkdir()

        val imageFile = File.createTempFile(
                imageFileName, /* prefix */
                ".jpg", /* suffix */
                storageDir     /* directory */
        )

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = imageFile.absolutePath
        return imageFile
    }

    private fun galleryIntent() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select File"), SELECT_FILE_CODE)
    }

    private fun getLatLngFromPhoto(data: Intent): LatLng? {
        val uri = data.data
        var cursor = this@MapFragment.context!!.contentResolver.query(
                uri,
                null,
                null,
                null,
                null)
        cursor.moveToFirst()
        val columnIndexOfDisplayName = 2
        val fileName = cursor.getString(columnIndexOfDisplayName)
        cursor.close()
        val selection = "${MediaStore.Images.ImageColumns.DISPLAY_NAME}='$fileName'"
        cursor = this@MapFragment.context!!.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.LATITUDE,
                        MediaStore.Images.Media.LONGITUDE),
                selection,
                null,
                null)
        var metaData: LatLng? = null
        if (cursor.count == 1) {
            cursor.moveToFirst()
            metaData = LatLng(cursor.getDouble(0), cursor.getDouble(1))
        }
        cursor.close()
        return metaData
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