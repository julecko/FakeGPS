package sk.dilino.fakegps.util

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

object PermissionsHelper {
    private const val LOCATION_PERMISSION_CODE = 1

    fun hasLocationPermission(activity: Activity): Boolean {
        return ActivityCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_CODE
        )
    }
}
