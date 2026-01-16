package sk.dilino.fakegps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
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

        findViewById<Button>(R.id.startMockBtn).setOnClickListener {
            val center = map.cameraPosition.target
            startMockLocationService(center.latitude, center.longitude)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isZoomControlsEnabled = true

        val initial = LatLng(48.1486, 17.1077)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(initial, 15f))
    }

    private fun startMockLocationService(lat: Double, lon: Double) {
        val intent = Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra("lat", lat)
            putExtra("lon", lon)
        }
        startForegroundService(intent)
    }
}
