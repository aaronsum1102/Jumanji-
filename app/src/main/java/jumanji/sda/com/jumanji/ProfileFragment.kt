package jumanji.sda.com.jumanji

import android.Manifest
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CircleOptions
import com.google.firebase.auth.FirebaseAuth
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

        var profileViewModel = ViewModelProviders.of(this)[ProfileViewModel::class.java]

        profileViewModel.getUserProfile(this.context!!)

        profileViewModel.userInfo?.observe(this, Observer {

            usernameText.text = it?.userName
            Picasso.get().load(it?.pictureURI).into(profilePhotoView);
        })

        signOutButton.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser?.displayName

            val profileViewModel = ViewModelProviders.of(this)[ProfileViewModel::class.java]

            profileViewModel.signOut()

            if (user != null) {
                Snackbar.make(it, "${user}, you are signed out", Snackbar.LENGTH_SHORT).show()
            } else {
                val userName = GoogleSignIn.getLastSignedInAccount(activity)?.givenName
                Snackbar.make(it, "${userName}, you are signed out", Snackbar.LENGTH_SHORT).show()
            }
        }
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
        userActivityMapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        userActivityMapView?.onLowMemory()
        super.onLowMemory()
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        map = googleMap ?: return
        val viewModel = ViewModelProviders.of(this@ProfileFragment)[LocationViewModel::class.java]
        val context = this@ProfileFragment.context ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            viewModel.getLastKnownLocation(map, 14.5f)
            map.setOnCameraIdleListener {
                map.clear()
                val currentLocation = map.cameraPosition.target //TODO to listen to GPS for location.
                map.addCircle(CircleOptions().radius(5.0)
                        .fillColor(Color.GREEN)
                        .strokeColor(Color.GREEN)
                        .center(currentLocation))
            }
        }
    }
}
