package sk.dilino.fakegps.mock_location

import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.SystemClock
import android.util.Log

class MockLocationInjector(
    private val locationManager: LocationManager
) {

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    fun setupProviders() {
        Log.d("TEST", "TEST")
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

    fun inject(lat: Double, lon: Double, speed: Float = 0f, bearing: Float = 0f): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtimeNanos()

        var success = true

        for (provider in providers) {
            val location = Location(provider).apply {
                latitude = lat
                longitude = lon

                // 🔒 REALISTIC STATIC VALUES
                accuracy = 6f          // meters (5–10m = normal GPS)
                altitude = 250.0       // meters above sea level (pick something sane)
                this.speed = speed     // m/s, 0 when stationary
                this.bearing = bearing // degrees, direction of travel

                time = now
                elapsedRealtimeNanos = elapsed
            }
            try {
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: SecurityException) {
                success = false
            } catch (e: IllegalArgumentException) {
                success = false
            }
        }
        return success
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
