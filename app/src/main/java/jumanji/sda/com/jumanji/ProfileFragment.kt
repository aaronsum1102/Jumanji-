package jumanji.sda.com.jumanji

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_profile.*


class ProfileFragment : Fragment(), OnMapReadyCallback {
    lateinit var map: GoogleMap

    override fun onAttach(context: Context?) {
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userActivityMapView.onCreate(savedInstanceState)

        userActivityMapView.onCreate(savedInstanceState)
        userActivityMapView.getMapAsync(this)

        val profileViewModel = ViewModelProviders.of(this)[ProfileViewModel::class.java]
        var username: String? = ""

        profileViewModel.userInfo?.observe(this, Observer {
            username = it?.userName
            usernameText.text = username
            Picasso.get().load(it?.photoURL).into(profilePhotoView)
        })

        signOutButton.setOnClickListener {
            val builder = AlertDialog.Builder(this.requireContext())
            builder.setTitle(R.string.app_name)
            builder.setMessage("Do you want to sign out?")
            builder.setPositiveButton("Yes") { dialog, id ->
                dialog.dismiss()
                profileViewModel.checkIfUserSignedIn(this.requireContext())
                goToSignIn()
            }
            builder.setNegativeButton("No") { dialog, id -> dialog.dismiss() }
            val alert = builder.create()
            alert.show()
        }
    }

    fun goToSignIn() {
        val intent = Intent(context, SignInActivity::class.java)
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        userActivityMapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        userActivityMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        userActivityMapView.onPause()
    }

    override fun onDestroy() {
        userActivityMapView?.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        userActivityMapView?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        userActivityMapView?.onLowMemory()
        super.onLowMemory()
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap ?: return
        map.isIndoorEnabled = false
        val zoomLevelForProfile = 14.5f
        val locationViewModel = ViewModelProviders.of(activity!!)[LocationViewModel::class.java]
        locationViewModel.moveToLastKnowLocation(map, zoomLevelForProfile)
        val context = this@ProfileFragment.context ?: return
        if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = false
            map.uiSettings.isZoomGesturesEnabled = false
        } else {
            Toast.makeText(context,
                    "Please enable permission to access your device location.",
                    Toast.LENGTH_LONG)
                    .show()
        }

        val mapAdapter = MapFragment.GoogleMapAdapter()
        mapAdapter.map = map
        val pinViewModel = ViewModelProviders.of(activity!!)[PinViewModel::class.java]
        pinViewModel.trashMarkers.observe(this, Observer { trashLocationMarkers ->
            if (trashLocationMarkers != null) {
                mapAdapter.trashLocationMarkers = trashLocationMarkers.map { marker ->
                    map.addMarker(MarkerOptions()
                            .position(marker.position)
                            .visible(false))
                }
            }
            mapAdapter.bindMarkers()
        })

        map.setOnCameraIdleListener {
            mapAdapter.bindMarkers()
        }
    }
}
