package sk.dilino.fakegps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import sk.dilino.fakegps.service.MockLocationService
import sk.dilino.fakegps.util.PermissionsHelper
import sk.dilino.fakegps.util.PermissionsHelper.hasLocationPermission
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private enum class Mode { SINGLE, ROUTE }

    private lateinit var map: GoogleMap
    private var mocking = false
    private var mode = Mode.SINGLE

    private val waypoints = mutableListOf<LatLng>()
    private var speedKmh = 5.0

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    private val fallbackLocation = LatLng(21.309753, -157.858439)

    private lateinit var toggleBtn: MaterialButton
    private lateinit var routeInfo: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val followRunnable = object : Runnable {
        override fun run() {
            if (mocking) {
                followMockPosition()
                updateRouteInfo()
                if (MockLocationService.arrived && !MockLocationService.arrivalShown) {
                    MockLocationService.arrivalShown = true
                    showArrivedDialog()
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermissionsIfNeeded()

        findViewById<View>(R.id.helpBtn).setOnClickListener { showTutorial() }

        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (!prefs.getBoolean("tutorial_seen", false)) {
            prefs.edit().putBoolean("tutorial_seen", true).apply()
            showTutorial()
        }

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map)
                    as SupportMapFragment
        mapFragment.getMapAsync(this)

        toggleBtn = findViewById(R.id.mockToggleBtn)
        routeInfo = findViewById(R.id.routeInfo)

        mocking = isMockRunning()
        if (prefs.getString("mode", null) == Mode.ROUTE.name) mode = Mode.ROUTE
        applyButtonState(toggleBtn, mocking)

        findViewById<MaterialButtonToggleGroup>(R.id.modeGroup).addOnButtonCheckedListener { _, id, checked ->
            if (checked) switchMode(if (id == R.id.modeRouteBtn) Mode.ROUTE else Mode.SINGLE)
        }
        findViewById<Button>(R.id.addPointBtn).setOnClickListener {
            if (::map.isInitialized && mode == Mode.ROUTE && !mocking) {
                waypoints.add(map.cameraPosition.target)
                onRouteChanged()
            }
        }
        findViewById<Button>(R.id.undoBtn).setOnClickListener {
            if (waypoints.isNotEmpty()) waypoints.removeAt(waypoints.lastIndex)
            onRouteChanged()
        }
        findViewById<Button>(R.id.clearBtn).setOnClickListener {
            waypoints.clear()
            onRouteChanged()
        }
        findViewById<Button>(R.id.speedBtn).setOnClickListener { showSpeedDialog() }
        findViewById<View>(R.id.myLocationBtn).setOnClickListener { centerOnRealLocation() }

        toggleBtn.setOnClickListener { onToggleClicked() }

        updateModeUi()
    }

    override fun onResume() {
        super.onResume()
        handler.post(followRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(followRunnable)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        map.uiSettings.apply {
            isMyLocationButtonEnabled = false
            isZoomControlsEnabled = false
        }
        if (mocking) setMapLocked(true)

        if (hasLocationPermission(this)) {
            getCurrentLocation()
        } else {
            moveMapTo(fallbackLocation)
        }
    }

    // ---------- Permissions ----------

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (!hasLocationPermission(this)) {
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
    }

    // ---------- Modes ----------

    private fun switchMode(newMode: Mode) {
        if (mocking || mode == newMode) return
        mode = newMode
        getSharedPreferences("app", MODE_PRIVATE).edit().putString("mode", mode.name).apply()
        updateModeUi()
    }

    private fun updateModeUi() {
        val group = findViewById<MaterialButtonToggleGroup>(R.id.modeGroup)
        group.check(if (mode == Mode.ROUTE) R.id.modeRouteBtn else R.id.modeSingleBtn)
        // Mode can't be changed while faking is on.
        findViewById<View>(R.id.modeSingleBtn).isEnabled = !mocking
        findViewById<View>(R.id.modeRouteBtn).isEnabled = !mocking

        // Real GPS is overridden by the mock while faking, so centering is only offered when idle.
        findViewById<View>(R.id.myLocationBtn).visibility = if (mocking) View.GONE else View.VISIBLE

        findViewById<View>(R.id.routeButtons).visibility =
            if (mode == Mode.ROUTE && !mocking) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.speedBtn).text = getString(R.string.speed_label, formatSpeed(speedKmh))

        redrawRoute()
        updateRouteInfo()
    }

    // ---------- Start / stop ----------

    private fun onToggleClicked() {
        if (!::map.isInitialized) return

        if (!mocking) {
            if (mode == Mode.SINGLE) {
                val center = map.cameraPosition.target
                startMock(center.latitude, center.longitude, null)
            } else {
                if (waypoints.size < 2) {
                    Toast.makeText(this, R.string.route_need_two, Toast.LENGTH_SHORT).show()
                    return
                }
                val flat = DoubleArray(waypoints.size * 2)
                waypoints.forEachIndexed { i, p ->
                    flat[i * 2] = p.latitude
                    flat[i * 2 + 1] = p.longitude
                }
                startMock(waypoints[0].latitude, waypoints[0].longitude, flat)
            }
            setMapLocked(true)
            mocking = true
        } else {
            stopMock()
            setMapLocked(false)
            mocking = false
        }
        applyButtonState(toggleBtn, mocking)
        updateModeUi()
    }

    private fun startMock(lat: Double, lon: Double, route: DoubleArray?) {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LAT, lat)
            putExtra(MockLocationService.EXTRA_LON, lon)
            if (route != null) {
                putExtra(MockLocationService.EXTRA_ROUTE, route)
                putExtra(MockLocationService.EXTRA_SPEED_MPS, (speedKmh / 3.6).toFloat())
            }
        }.also(::startForegroundService)
    }

    private fun stopMock() {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }.also(::startService)
    }

    // ---------- Route ----------

    private fun onRouteChanged() {
        redrawRoute()
        updateRouteInfo()
    }

    private fun redrawRoute() {
        if (!::map.isInitialized) return
        map.clear()
        if (mode != Mode.ROUTE) return
        waypoints.forEachIndexed { i, p ->
            map.addMarker(
                MarkerOptions().position(p).title("${i + 1}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }
        if (waypoints.size >= 2) {
            map.addPolyline(PolylineOptions().addAll(waypoints).width(10f).color(Color.parseColor("#2962FF")))
        }
    }

    private fun routeLengthMeters(): Double {
        var total = 0.0
        val out = FloatArray(1)
        for (i in 0 until waypoints.size - 1) {
            Location.distanceBetween(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude, out
            )
            total += out[0]
        }
        return total
    }

    private fun updateRouteInfo() {
        if (!::routeInfo.isInitialized) return
        if (mode == Mode.SINGLE) {
            routeInfo.text = if (mocking) {
                "Faking location\n%.5f, %.5f".format(MockLocationService.currentLat, MockLocationService.currentLon)
            } else {
                "Move the map to choose a location"
            }
            return
        }
        val speedMps = speedKmh / 3.6
        routeInfo.text = when {
            mocking && MockLocationService.arrived ->
                "Arrived. Still faking this location until you stop."
            mocking && MockLocationService.routeActive ->
                "Remaining ${formatDistance(MockLocationService.remainingMeters)} · " +
                        formatDuration(MockLocationService.remainingMeters / speedMps)
            mocking -> "Starting…"
            waypoints.size < 2 -> "${getString(R.string.route_hint)} (${waypoints.size} added)"
            else -> {
                val length = routeLengthMeters()
                "${waypoints.size} points · ${formatDistance(length)} · " +
                        "${formatDuration(length / speedMps)} at ${formatSpeed(speedKmh)}"
            }
        }
    }

    private fun showSpeedDialog() {
        val presets = listOf(
            "Walk (5 km/h)" to 5.0,
            "Jog (8 km/h)" to 8.0,
            "Run (10 km/h)" to 10.0,
            "Bike (20 km/h)" to 20.0,
            "Car (50 km/h)" to 50.0
        )
        val items = (presets.map { it.first } + getString(R.string.speed_custom)).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.speed_title)
            .setItems(items) { _, which ->
                if (which < presets.size) setSpeed(presets[which].second) else showCustomSpeedDialog()
            }
            .show()
    }

    private fun showCustomSpeedDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.speed_custom_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.speed_title)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val value = input.text.toString().toDoubleOrNull()
                if (value != null && value > 0) setSpeed(value.coerceAtMost(500.0))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setSpeed(kmh: Double) {
        speedKmh = kmh
        findViewById<Button>(R.id.speedBtn).text = getString(R.string.speed_label, formatSpeed(kmh))
        updateRouteInfo()
    }

    private fun showArrivedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.arrived_title)
            .setMessage(R.string.arrived_message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun formatSpeed(kmh: Double): String =
        if (kmh % 1.0 == 0.0) "${kmh.toInt()} km/h" else "%.1f km/h".format(kmh)

    private fun formatDistance(meters: Double): String =
        if (meters >= 1000) "%.2f km".format(meters / 1000) else "${meters.roundToInt()} m"

    private fun formatDuration(seconds: Double): String {
        val total = seconds.roundToInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "${h} h ${m} min"
            m > 0 -> "${m} min ${s} s"
            else -> "${s} s"
        }
    }

    // ---------- Map helpers ----------

    private fun followMockPosition() {
        if (!::map.isInitialized) return
        val lat = MockLocationService.currentLat
        val lon = MockLocationService.currentLon
        if (lat != 0.0 || lon != 0.0) {
            map.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat, lon)))
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

    @SuppressLint("MissingPermission")
    private fun centerOnRealLocation() {
        if (mocking || !::map.isInitialized) return
        if (!hasLocationPermission(this)) {
            requestPermissionsIfNeeded()
            return
        }
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (mocking) return@addOnSuccessListener
                if (location != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 17f)
                    )
                } else {
                    Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    private fun moveMapTo(latLng: LatLng) {
        if (::map.isInitialized) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun setMapLocked(locked: Boolean) {
        map.uiSettings.apply {
            isScrollGesturesEnabled = !locked
            isZoomGesturesEnabled = !locked
            isRotateGesturesEnabled = !locked
            isTiltGesturesEnabled = !locked
        }
    }

    private fun isMockRunning(): Boolean {
        return getSharedPreferences("mock_location", MODE_PRIVATE)
            .getBoolean("running", false)
    }

    private fun applyButtonState(btn: MaterialButton, running: Boolean) {
        if (running) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
            btn.setIconResource(R.drawable.ic_stop)
            btn.setText(R.string.stop_faking)
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00A152"))
            btn.setIconResource(R.drawable.ic_play)
            btn.setText(R.string.start_faking)
        }
    }

    // ---------- Tutorial ----------

    private fun showTutorial(step: Int = 0) {
        val steps = intArrayOf(
            R.string.tutorial_step_1,
            R.string.tutorial_step_2,
            R.string.tutorial_step_3,
            R.string.tutorial_step_4
        )
        val last = step == steps.lastIndex
        val builder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.tutorial_title, step + 1, steps.size))
            .setMessage(steps[step])
            .setPositiveButton(if (last) R.string.tutorial_done else R.string.tutorial_next) { _, _ ->
                if (!last) showTutorial(step + 1)
            }
        if (step > 0) {
            builder.setNegativeButton(R.string.tutorial_back) { _, _ -> showTutorial(step - 1) }
        }
        if (step == 1) {
            builder.setNeutralButton(R.string.tutorial_open_dev) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
        builder.show()
    }
}
