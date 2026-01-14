package sk.dilino.fakegps.mock_location

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock

class MockLocationInjector(
    private val locationManager: LocationManager
) {

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    fun setupProviders() {
        for (provider in providers) {
            try {
                locationManager.addTestProvider(
                    provider,
                    false,  // requiresNetwork
                    false,  // requiresSatellite
                    false,  // requiresCell
                    false,  // hasMonetaryCost
                    true,   // supportsAltitude
                    true,   // supportsSpeed
                    true,   // supportsBearing
                    ProviderProperties.POWER_USAGE_LOW,
                    ProviderProperties.ACCURACY_FINE
                )
            } catch (_: Exception) {}

            try {
                locationManager.setTestProviderEnabled(provider, true)
            } catch (_: Exception) {}
        }
    }

    fun inject(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            val location = Location(provider).apply {
                latitude = lat
                longitude = lon

                // 🔒 REALISTIC STATIC VALUES
                accuracy = 6f          // meters (5–10m = normal GPS)
                altitude = 250.0       // meters above sea level (pick something sane)
                speed = 0f             // stationary
                bearing = 0f           // no movement → no direction

                time = now
                elapsedRealtimeNanos = elapsed
            }

            try {
                locationManager.setTestProviderLocation(provider, location)
            } catch (_: SecurityException) {}
        }
    }

    fun cleanup() {
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {}
        }
    }
}
