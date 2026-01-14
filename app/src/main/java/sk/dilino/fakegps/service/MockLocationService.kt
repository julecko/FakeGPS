package sk.dilino.fakegps.service

import android.app.*
import android.content.Intent
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import sk.dilino.fakegps.mock_location.MockLocationInjector

class MockLocationService : Service() {

    private lateinit var injector: MockLocationInjector
    private val handler = Handler(Looper.getMainLooper())

    private var lat = 0.0
    private var lon = 0.0

    private val updateRunnable = object : Runnable {
        override fun run() {
            lat = jitter(lat)
            lon = jitter(lon)

            injector.inject(lat, lon)
            handler.postDelayed(this, 50)
        }
    }

    private fun jitter(value: Double): Double {
        return value + (Math.random() - 0.5) * 0.00002
    }

    override fun onCreate() {
        super.onCreate()

        val locationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        injector = MockLocationInjector(locationManager)
        injector.setupProviders()

        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lat = intent?.getDoubleExtra("lat", 0.0) ?: lat
        lon = intent?.getDoubleExtra("lon", 0.0) ?: lon

        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        injector.cleanup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, "mock_location")
            .setContentTitle("Fake GPS Running")
            .setContentText("Mock location active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "mock_location",
                "Mock Location",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
