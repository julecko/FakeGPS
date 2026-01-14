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
import sk.dilino.fakegps.service.MockLocationService

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

            val intent = Intent(this, MockLocationService::class.java).apply {
                putExtra("lat", lat)
                putExtra("lon", lon)
            }

            startForegroundService(intent)
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