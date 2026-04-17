package app.onloc.locationclient

import android.Manifest
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.onloc.locationclient.ui.theme.LocationClientTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (!permissions.values.any { it }) {
                onDestroy()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        val oneTimeConfig = locationClientConfig {}

        val runningConfig = locationClientConfig {
            requiredTimeInterval = 2L * 1000L
            requiredDistanceInterval = 12f
        }

        setContent {
            LocationClientTheme {
                val scope = rememberCoroutineScope()

                val oneTimeClient = LocationClient(applicationContext, oneTimeConfig)
                val runningClient = LocationClient(applicationContext, runningConfig)

                var currentLocation by rememberSaveable { mutableStateOf<Location?>(null) }
                var currentLocationLoading by rememberSaveable { mutableStateOf(false) }

                var lastKnownLocation by rememberSaveable { mutableStateOf<Location?>(null) }
                var lastKnownLocationLoading by rememberSaveable { mutableStateOf(false) }

                var runningLocation by rememberSaveable { mutableStateOf<Location?>(null) }

                LaunchedEffect(Unit) {
                    runningClient.locationFlow().collect { result ->
                        result.onSuccess {
                            runningLocation = it
                        }
                        result.onFailure {
                            it.printStackTrace()
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding), contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(innerPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LocationCard(
                                title = "Current Location",
                                location = currentLocation,
                                isLoading = currentLocationLoading,
                                onButtonClick = {
                                    scope.launch {
                                        currentLocationLoading = true

                                        oneTimeClient.locationFlow().first()
                                            .onSuccess { location ->
                                                println(location)
                                                currentLocation = location
                                            }
                                            .onFailure { error ->
                                                error.printStackTrace()
                                            }

                                        currentLocationLoading = false
                                    }
                                },
                            )

                            LocationCard(
                                title = "Last Known Location",
                                location = lastKnownLocation,
                                isLoading = lastKnownLocationLoading,
                                onButtonClick = {
                                    lastKnownLocationLoading = true
                                    val result = oneTimeClient.getLastKnownLocation()
                                    result.onSuccess {
                                        lastKnownLocation = it
                                    }
                                    lastKnownLocationLoading = false
                                }
                            )

                            LocationCard(
                                title = "Running Location",
                                location = runningLocation,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationCard(
    title: String,
    location: Location?,
    isLoading: Boolean = false,
    onButtonClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            onButtonClick?.let { click ->
                Button(
                    onClick = click,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = "Request")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            location?.let {
                Text(
                    text = "Longitude: ${it.longitude}",
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Latitude: ${it.latitude}",
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Accuracy: ${it.accuracy}",
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Provider: ${it.provider}",
                    textAlign = TextAlign.Center,
                )
            } ?: Text(
                text = "Waiting for location...",
                textAlign = TextAlign.Center,
                color = Color.Gray,
            )
        }
    }
}
