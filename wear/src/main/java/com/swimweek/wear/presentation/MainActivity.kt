package com.swimweek.wear.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.swimweek.wear.R
import com.swimweek.wear.complication.ComplicationFormat
import com.swimweek.wear.data.WeeklySwim

/** Minimal screen opened from the complication tap action. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeeklyApp() }
    }
}

@Composable
fun WeeklyApp() {
    val context = LocalContext.current
    var data by remember { mutableStateOf<WeeklySwim?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        data = WeeklySwim.load(context)
        loaded = true
    }
    WeeklyScreen(data = data, loaded = loaded)
}

@Composable
fun WeeklyScreen(data: WeeklySwim?, loaded: Boolean) {
    MaterialTheme {
        AppScaffold(timeText = { TimeText() }) {
            ScreenScaffold {
                if (data != null && data.targetMeters > 0) {
                    CircularProgressIndicator(
                        progress = { (data.distanceMeters / data.targetMeters).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.this_week), style = MaterialTheme.typography.labelMedium)
                    if (data == null) {
                        if (loaded) {
                            Text(
                                stringResource(R.string.no_data),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    } else {
                        Text(
                            ComplicationFormat.fullValue(data.distanceMeters, data.unit),
                            style = MaterialTheme.typography.displaySmall,
                        )
                        if (data.targetMeters > 0) {
                            Text(
                                stringResource(R.string.of_target, ComplicationFormat.fullValue(data.targetMeters, data.unit)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            stringResource(R.string.sessions, data.sessions),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_LARGE_ROUND, showSystemUi = true)
@Composable
private fun WeeklyScreenPreview() {
    WeeklyScreen(WeeklySwim(2_400.0, 4_000.0, "METERS", 2, 0L), loaded = true)
}
