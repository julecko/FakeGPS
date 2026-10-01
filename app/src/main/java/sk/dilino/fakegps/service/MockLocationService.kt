package sk.dilino.fakegps.service

import android.app.*
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import sk.dilino.fakegps.MainActivity
import sk.dilino.fakegps.mock_location.MockLocationInjector

class MockLocationService : Service() {

    companion object {
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        /** Flat array of lat,lon pairs. When present the service walks this route. */
        const val EXTRA_ROUTE = "route"
        const val EXTRA_SPEED_MPS = "speed_mps"

        private const val TICK_MS = 50L
        private const val CHANNEL_MOCK = "mock_location"
        private const val CHANNEL_ARRIVAL = "arrival"
        private const val NOTIFICATION_MOCK = 1
        private const val NOTIFICATION_ARRIVAL = 2

        // State shared with the UI (same process).
        @Volatile var currentLat = 0.0
        @Volatile var currentLon = 0.0
        @Volatile var remainingMeters = 0.0
        @Volatile var routeActive = false
        /** True once the route end was reached. The fake location keeps running afterwards. */
        @Volatile var arrived = false
        /** Set by the UI once it has shown the "arrived" dialog. */
        @Volatile var arrivalShown = false
    }

    private lateinit var injector: MockLocationInjector

    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler

    private var lat = 0.0
    private var lon = 0.0
    private var bearing = 0f
    private var running = false

    // Route state
    private var route: List<DoubleArray> = emptyList()
    private var segmentLengths: List<Float> = emptyList()
    private var totalLength = 0.0
    private var travelled = 0.0
    private var speedMps = 0f

    private val updateRunnable = object : Runnable {
        override fun run() {
            var speed = 0f
            if (routeActive) {
                speed = advanceRoute()
            }
            currentLat = lat
            currentLon = lon
            val ok = injector.inject(lat, lon, speed, bearing)
            if (!ok) {
                stopSelf()
                return
            }
            handler.postDelayed(this, TICK_MS)
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

        createNotificationChannels()
        startForeground(NOTIFICATION_MOCK, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lat = intent.getDoubleExtra(EXTRA_LAT, lat)
                lon = intent.getDoubleExtra(EXTRA_LON, lon)
                val flat = intent.getDoubleArrayExtra(EXTRA_ROUTE)
                if (flat != null && flat.size >= 4) {
                    setupRoute(flat, intent.getFloatExtra(EXTRA_SPEED_MPS, 1.4f))
                } else {
                    routeActive = false
                }
                startMocking()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun setupRoute(flat: DoubleArray, speed: Float) {
        route = (0 until flat.size / 2).map { doubleArrayOf(flat[it * 2], flat[it * 2 + 1]) }
        segmentLengths = route.zipWithNext { a, b -> distance(a, b) }
        totalLength = segmentLengths.sumOf { it.toDouble() }
        travelled = 0.0
        speedMps = speed
        lat = route[0][0]
        lon = route[0][1]
        remainingMeters = totalLength
        arrived = false
        arrivalShown = false
        routeActive = true
    }

    /** Moves along the route by one tick. Returns the current speed in m/s. */
    private fun advanceRoute(): Float {
        travelled = minOf(totalLength, travelled + speedMps * TICK_MS / 1000.0)
        remainingMeters = totalLength - travelled

        var segmentStart = 0.0
        for (i in segmentLengths.indices) {
            val len = segmentLengths[i].toDouble()
            val isLast = i == segmentLengths.lastIndex
            if (travelled <= segmentStart + len || isLast) {
                val t = if (len > 0) ((travelled - segmentStart) / len).coerceIn(0.0, 1.0) else 1.0
                val a = route[i]
                val b = route[i + 1]
                lat = a[0] + (b[0] - a[0]) * t
                lon = a[1] + (b[1] - a[1]) * t
                if (len > 0) bearing = bearingOf(a, b)
                break
            }
            segmentStart += len
        }

        if (travelled >= totalLength) {
            // Arrived: stay at the final point, keep faking until the user stops it.
            routeActive = false
            remainingMeters = 0.0
            arrived = true
            notifyArrival()
            return 0f
        }
        return speedMps
    }

    private fun distance(a: DoubleArray, b: DoubleArray): Float {
        val out = FloatArray(1)
        Location.distanceBetween(a[0], a[1], b[0], b[1], out)
        return out[0]
    }

    private fun bearingOf(a: DoubleArray, b: DoubleArray): Float {
        val out = FloatArray(2)
        Location.distanceBetween(a[0], a[1], b[0], b[1], out)
        return (out[1] + 360f) % 360f
    }

    private fun startMocking() {
        if (running) return
        running = true
        currentLat = lat
        currentLon = lon
        setRunningState(true)
        handler.post(updateRunnable)
    }

    override fun onDestroy() {
        running = false
        routeActive = false
        arrived = false

        setRunningState(false)

        handler.removeCallbacks(updateRunnable)
        handlerThread.quitSafely()

        injector.cleanup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_MOCK)
            .setContentTitle("Fake GPS Running")
            .setContentText("Mock location active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

    private fun notifyArrival() {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ARRIVAL)
            .setContentTitle("Destination reached")
            .setContentText("You arrived. Fake GPS keeps running until you stop it.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ARRIVAL, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MOCK, "Mock Location", NotificationManager.IMPORTANCE_LOW)
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ARRIVAL, "Arrival", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun setRunningState(value: Boolean) {
        getSharedPreferences("mock_location", MODE_PRIVATE)
            .edit()
            .putBoolean("running", value)
            .apply()
    }
}
