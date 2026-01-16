package sk.dilino.fakegps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import sk.dilino.fakegps.service.MockLocationService
import sk.dilino.fakegps.util.MockLocationUtils
import sk.dilino.fakegps.util.PermissionsHelper
import sk.dilino.fakegps.util.PermissionsHelper.hasLocationPermission
import com.google.android.libraries.places.api.model.Place

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var mocking = false

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val fallbackLocation = LatLng(21.309753, -157.858439)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Places.isInitialized()) {
            Places.initialize(this, "YOUR_API_KEY")
        }

        if (!hasLocationPermission(this)) {
            PermissionsHelper.requestLocationPermission(this)
        }

        MockLocationUtils.checkAndPromptMockLocation(this)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map)
                    as SupportMapFragment
        mapFragment.getMapAsync(this)

        val toggleBtn = findViewById<ImageButton>(R.id.mockToggleBtn)
        toggleBtn.setOnClickListener {
            val googleMap = map ?: return@setOnClickListener

            if (!mocking) {
                val center = googleMap.cameraPosition.target
                setMapLocked(true)

                startMock(center.latitude, center.longitude)

                toggleBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
                toggleBtn.setImageResource(R.drawable.ic_stop)
                toggleBtn.imageTintList = ColorStateList.valueOf(Color.BLACK)

                mocking = true
            } else {
                stopMock()

                setMapLocked(false)

                toggleBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00C853"))
                toggleBtn.setImageResource(R.drawable.ic_play)
                toggleBtn.imageTintList = ColorStateList.valueOf(Color.BLACK)

                mocking = false
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        map.uiSettings.apply {
            isMyLocationButtonEnabled = false
            isZoomControlsEnabled = false
        }

        if (hasLocationPermission(this)) {
            getCurrentLocation()
        } else {
            moveMapTo(fallbackLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (!hasLocationPermission(this)) {
            Toast.makeText(this, "No location permission", Toast.LENGTH_SHORT).show()
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            val target = if (location != null) {
                LatLng(location.latitude, location.longitude)
            } else {
                fallbackLocation
            }
            moveMapTo(target)
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to get location – using fallback", Toast.LENGTH_SHORT).show()
            moveMapTo(fallbackLocation)
        }
    }

    private fun moveMapTo(latLng: LatLng) {
        if (::map.isInitialized) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun startMock(lat: Double, lon: Double) {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra("lat", lat)
            putExtra("lon", lon)
        }.also(::startForegroundService)
    }

    private fun stopMock() {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }.also(::startService)
    }

    private fun setMapLocked(locked: Boolean) {
        map.uiSettings.apply {
            isScrollGesturesEnabled = !locked
            isZoomGesturesEnabled = !locked
            isRotateGesturesEnabled = !locked
            isTiltGesturesEnabled = !locked
            isMyLocationButtonEnabled = !locked
        }
    }
}
