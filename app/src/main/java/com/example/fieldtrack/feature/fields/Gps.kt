package com.example.fieldtrack.feature.fields

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.fieldtrack.data.measure.Fix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Whether the phone can give the app its GPS, and if not, why not. */
enum class GpsAccess {
    Allowed,
    NotPermitted,

    /** Allowed, but only the approximate position Android 12 lets people choose instead. */
    NotPrecise,
    SwitchedOff,
}

/**
 * Precise location only: a walk round a plot is metres, and the approximate
 * position Android 12 lets people grant in its place is hundreds of them.
 */
fun gpsAccess(context: Context): GpsAccess {
    val precise = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!precise) {
        val approximate = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return if (approximate) GpsAccess.NotPrecise else GpsAccess.NotPermitted
    }
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return if (manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) GpsAccess.Allowed
    else GpsAccess.SwitchedOff
}

/**
 * The GPS's readings, about one a second, for as long as somebody collects them.
 *
 * The platform's own [LocationManager], as [currentPin] uses, so there is no
 * library behind it. Updates stop the moment collection does: nothing keeps the
 * GPS running when the screen that wants it is gone.
 */
fun gpsFixes(context: Context): Flow<Fix> = callbackFlow {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            trySend(
                Fix(
                    location.latitude, location.longitude,
                    if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                    location.time,
                )
            )
        }

        // Required before Android 11, where they are not optional.
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Never called from Android 10 on.")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    // Refused if the permission went between the check and here; the screen's own
    // check then shows the setup card, so nothing is lost by ending quietly.
    runCatching {
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
    }.onFailure { close() }
    awaitClose { manager.removeUpdates(listener) }
}
