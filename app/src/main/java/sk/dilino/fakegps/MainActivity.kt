package sk.dilino.fakegps

import android.Manifest
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import sk.dilino.fakegps.service.MockLocationService
import sk.dilino.fakegps.util.MockLocationUtils
import sk.dilino.fakegps.util.PermissionsHelper

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private var mocking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!PermissionsHelper.hasLocationPermission(this)) {
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

        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isZoomControlsEnabled = false

        val initial = LatLng(48.1486, 17.1077)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initial, 15f))
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
