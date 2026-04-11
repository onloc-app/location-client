package app.onloc.locationclient

import android.location.Location

sealed class LocationResult {
    data class Success(val location: Location?) : LocationResult()
    object PermissionDenied : LocationResult()
    object ProvidersDisabled : LocationResult()
}