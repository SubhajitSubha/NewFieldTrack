package com.example.fieldtrack.feature.fields

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Where the phone is, or why it could not be found. */
sealed interface PinResult {
    data class Found(val latitude: Double, val longitude: Double) : PinResult

    /** The permission is not granted, so there is nothing to ask the system for. */
    data object NotAllowed : PinResult

    /** Allowed, but no fix — indoors, or location switched off on the device. */
    data object Nowhere : PinResult
}

/**
 * Where this phone is standing, for pinning a field.
 *
 * The platform's own [LocationManager] rather than Play Services. A plot is
 * hundreds of metres across and is pinned once, by somebody standing in it, so
 * fused location and its dependency buy nothing here.
 *
 * Every path returns a [PinResult] rather than throwing or hanging. The caller
 * is a farmer in a field with one bar of signal, and the only acceptable
 * outcomes are a pin or a sentence saying why not — never a spinner that never
 * stops.
 */
suspend fun currentPin(context: Context): PinResult {
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return PinResult.NotAllowed

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return PinResult.Nowhere

    // The provider the granted permission actually allows. Asking GPS for a fix
    // on coarse-only permission throws, and that throw would reach a farmer as
    // a crash rather than as "try again out in the open".
    val provider = when {
        fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
            LocationManager.GPS_PROVIDER
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
            LocationManager.NETWORK_PROVIDER
        else -> return PinResult.Nowhere
    }

    // A fix the device already has, when it is recent. Standing in a field
    // waiting for a satellite is a poor way to spend a minute, and a position
    // from two minutes ago is the same field.
    runCatching { manager.getLastKnownLocation(provider) }
        .getOrNull()
        ?.takeIf { System.currentTimeMillis() - it.time < RECENT_ENOUGH_MILLIS }
        ?.let { return it.asPin() }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        // No getCurrentLocation before API 30. Rather than hand-rolling a
        // listener with its own timeout, fall back to whatever fix exists at
        // any age — stale is better than nothing, and the screen says the pin
        // records where the phone is now.
        return runCatching { manager.getLastKnownLocation(provider) }
            .getOrNull()?.asPin() ?: PinResult.Nowhere
    }

    return suspendCancellableCoroutine { continuation ->
        val signal = android.os.CancellationSignal()
        val executor = Executors.newSingleThreadExecutor()
        continuation.invokeOnCancellation {
            runCatching { signal.cancel() }
            executor.shutdownNow()
        }
        runCatching {
            manager.getCurrentLocation(provider, signal, executor) { location ->
                executor.shutdown()
                if (continuation.isActive) {
                    continuation.resume(location?.asPin() ?: PinResult.Nowhere)
                }
            }
        }.onFailure {
            executor.shutdown()
            if (continuation.isActive) continuation.resume(PinResult.Nowhere)
        }
    }
}

private fun Location.asPin() = PinResult.Found(latitude, longitude)

/** Old enough to be worth replacing, young enough to be the same field. */
private const val RECENT_ENOUGH_MILLIS = 2L * 60 * 1000
