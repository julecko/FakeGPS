package sk.dilino.fakegps.service

import android.app.*
import android.content.Intent
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import sk.dilino.fakegps.mock_location.MockLocationInjector

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
    }

    private lateinit var injector: MockLocationInjector

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    private var lat = 0.0
    private var lon = 0.0
    private var running = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            val ok = injector.inject(lat, lon)
            if (!ok) {
                stopSelf()
                return
            }
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate() {
        super.onCreate()

        handlerThread = HandlerThread("MockLocationThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        val locationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        injector = MockLocationInjector(locationManager)
        injector.setupProviders()

        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lat = intent.getDoubleExtra("lat", lat)
                lon = intent.getDoubleExtra("lon", lon)
                startMocking()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startMocking() {
        if (running) return
        running = true
        setRunningState(true)
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        running = false

        setRunningState(false)

        handler.removeCallbacks(updateRunnable)
        handlerThread.quitSafely()

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

    private fun setRunningState(value: Boolean) {
        getSharedPreferences("mock_location", MODE_PRIVATE)
            .edit()
            .putBoolean("running", value)
            .apply()
    }
}
