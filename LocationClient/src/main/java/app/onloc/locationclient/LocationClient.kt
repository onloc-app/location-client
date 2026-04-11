package app.onloc.locationclient

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

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
    fun getLastKnownLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            return LocationResult.ProvidersDisabled
        }
        return LocationResult.Success(locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER))
    }

    /**
     * Gives periodic [Location] updates.
     *
     * @param owner Lifecycle owner. The updates will stop when the lifecycle is destroyed.
     * @param callback Callback triggered when a valid location is received.
     */
    fun requestLocationUpdates(owner: LifecycleOwner, callback: (LocationResult) -> Unit) {
        val stop = locationUpdates { result ->
            callback(result)
        }

        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                stop()
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    /**
     * Gives periodic [Location] updates. Returns a function to manually stop the service.
     *
     * @param callback Callback triggered when a valid location is received.
     *
     * @return Function that stops the updates when called.
     */
    fun requestLocationUpdates(callback: (LocationResult) -> Unit): () -> Unit {
        return locationUpdates { result ->
            callback(result)
        }
    }

    @Suppress("MissingPermission")
    private fun locationUpdates(callback: (LocationResult) -> Unit): () -> Unit {
        if (!hasPermission()) {
            callback(LocationResult.PermissionDenied)
            return {}
        }

        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            callback(LocationResult.ProvidersDisabled)
            return {}
        }

        val locations = mutableListOf<Location>()
        val handler = Handler(Looper.getMainLooper())

        lateinit var listener: LocationListener

        var started = false

        fun bestLocation(): Location? {
            return locations.minByOrNull { it.accuracy }
        }

        fun stopInternal() {
            locationManager.removeUpdates(listener)
            handler.removeCallbacksAndMessages(null)
        }

        val decisionRunnable = object : Runnable {
            override fun run() {
                val best = bestLocation()

                if (config.keepTracking) {
                    if (best != null) {
                        callback(LocationResult.Success(best))
                    }
                    locations.clear()
                    handler.postDelayed(this, config.requiredTimeInterval)
                } else {
                    callback(LocationResult.Success(best))
                    stopInternal()
                }
            }
        }

        listener = LocationListener { location ->
            if (!started) {
                started = true
                handler.postDelayed(decisionRunnable, config.requiredTimeInterval)
            }

            if (isLocationValid(location)) {
                locations.add(location)
            }
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

        return { stopInternal() }
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
