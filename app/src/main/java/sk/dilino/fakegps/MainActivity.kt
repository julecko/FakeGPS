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

class MainActivity : AppCompatActivity() {

    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val latInput = findViewById<EditText>(R.id.latInput)
        val lonInput = findViewById<EditText>(R.id.lonInput)
        val btn = findViewById<Button>(R.id.setLocationBtn)

        requestPermissions()

        openMockLocationSettingsIfNeeded()

        btn.setOnClickListener {
            val lat = latInput.text.toString().toDoubleOrNull()
            val lon = lonInput.text.toString().toDoubleOrNull()

            if (lat == null || lon == null) {
                Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setMockLocation(lat, lon)
        }
    }

    private fun requestPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    private fun setMockLocation(lat: Double, lon: Double) {
        try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE
            )
        } catch (_: Exception) {}

        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lon
            accuracy = 1f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = System.nanoTime()
        }

        try {
            locationManager.setTestProviderLocation(
                LocationManager.GPS_PROVIDER,
                location
            )
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                "App not selected as Mock Location provider",
                Toast.LENGTH_LONG
            ).show()
        }

        Toast.makeText(this, "Mock location set", Toast.LENGTH_SHORT).show()
    }

    private fun openMockLocationSettingsIfNeeded() {
        if (!Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            ).isNullOrEmpty()
        ) {
            return
        }

        Toast.makeText(
            this,
            "Select this app as Mock Location App",
            Toast.LENGTH_LONG
        ).show()

        startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        )
    }

}