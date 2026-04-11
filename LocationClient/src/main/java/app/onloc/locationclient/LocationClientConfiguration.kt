package app.onloc.locationclient

data class LocationClientConfiguration(
    val keepTracking: Boolean = false,
    val requiredTimeInterval: Long,
    val requiredDistanceInterval: Float,
    val acceptableAccuracy: Float,
    val acceptableTimePeriod: Long,
    val gpsMessage: String
)

class LocationClientConfigurationDSL {
    /**
     * Whether the client keeps fetching locations after the first one
     */
    var keepTracking: Boolean = false

    /**
     * The interval at which the location will update.
     */
    var requiredTimeInterval: Long = 0L

    /**
     * The minimum distance that needs to be traveled before updating the location
     */
    var requiredDistanceInterval: Float = 0f

    /**
     * The maximum accuracy a location can have.
     */
    var acceptableAccuracy: Float = Float.MAX_VALUE

    /**
     * The maximum time elapsed between when the location was fetched and now.
     */
    var acceptableTimePeriod: Long = Long.MAX_VALUE

    /**
     * Message displayed when GPS needs to be turned on.
     */
    var gpsMessage: String = "Turn on GPS?"

    fun build(): LocationClientConfiguration {
        return LocationClientConfiguration(
            keepTracking = keepTracking,
            requiredTimeInterval = requiredTimeInterval,
            requiredDistanceInterval = requiredDistanceInterval,
            acceptableAccuracy = acceptableAccuracy,
            acceptableTimePeriod = acceptableTimePeriod,
            gpsMessage = gpsMessage,
        )
    }
}

fun locationClientConfig(block: LocationClientConfigurationDSL.() -> Unit): LocationClientConfiguration {
    return LocationClientConfigurationDSL().apply(block).build()
}
