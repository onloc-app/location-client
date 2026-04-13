package app.onloc.locationclient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class LocationClient(private val context: Context, val config: LocationClientConfiguration) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Gets the last known [Location] of the device.
     *
     * This method does not check if the [Location] is valid with the given [LocationClientConfiguration].
     *
     * @return The device's last known [Location].
     */
    @Suppress("MissingPermission")
    fun getLastKnownLocation(): Result<Location?> {
        if (!hasPermission()) return Result.failure(Exception("Permission denied"))
        if (!locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            return Result.failure(Exception("Permission denied"))
        }
        return Result.success(locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER))
    }

    /**
     * Flow that emits locations based on the provided configuration.
     *
     * @return A [Flow] of [Result] containing a [Location].
     */
    fun locationFlow(): Flow<Result<Location>> {
        return rawLocationFlow()
            .bestInWindow(config.requiredTimeInterval)
            .map { result ->
                result.fold(
                    onSuccess = { location ->
                        if (isLocationValid(location)) {
                            Result.success(location)
                        } else {
                            Result.failure(Exception("Location is invalid"))
                        }
                    },
                    onFailure = { error ->
                        Result.failure(error)
                    },
                )
            }
    }

    @Suppress("MissingPermission")
    private fun rawLocationFlow(): Flow<Result<Location>> = callbackFlow {
        if (!hasPermission()) {
            trySend(Result.failure(Exception("Permission denied")))
            close()
            return@callbackFlow
        }

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            trySend(Result.failure(Exception("Providers disabled")))
            close()
            return@callbackFlow
        }

        val listener = LocationListener { location ->
            trySend(Result.success(location))
        }

        if (gpsEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                config.requiredTimeInterval,
                config.requiredDistanceInterval,
                listener,
                Looper.getMainLooper(),
            )
        }

        if (networkEnabled) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                config.requiredTimeInterval,
                config.requiredDistanceInterval,
                listener,
                Looper.getMainLooper(),
            )
        }

        awaitClose {
            locationManager.removeUpdates(listener)
        }
    }

    /**
     * Validates a [Location] with the current [LocationClientConfiguration].
     */
    private fun isLocationValid(location: Location): Boolean {
        var isValid = true

        if (location.accuracy > config.acceptableAccuracy) isValid = false

        val now = System.currentTimeMillis()
        if (now - location.time > config.acceptableTimePeriod) isValid = false

        return isValid
    }

    /**
     * Checks if either coarse or fine location permission has been granted.
     */
    private fun hasPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Flow<Result<Location>>.bestInWindow(
    windowMs: Long,
): Flow<Result<Location>> = flow {
    var best: Result<Location>? = null
    var windowEnd = 0L

    collect { result ->
        val now = System.currentTimeMillis()

        if (result.isFailure) {
            best?.let { emit(it) }
            best = null
            windowEnd = 0L
            emit(result)
            return@collect
        }

        if (now >= windowEnd) {
            best?.let { emit(it) }
            best = null
            emit(result)
            windowEnd = now + windowMs
        } else {
            val incoming = result.getOrThrow()
            val current = best?.getOrThrow()
            if (current == null || incoming.accuracy < current.accuracy) {
                best = result
            }
        }
    }

    best?.let { emit(it) }
}
