package com.swimweek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.swimweek.app.ui.home.HomeScreen
import com.swimweek.app.ui.onboarding.PermissionOnboardingScreen
import com.swimweek.app.ui.theme.SwimWeekTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwimWeekTheme {
                var showOnboarding by rememberSaveable { mutableStateOf(true) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (showOnboarding) {
                        PermissionOnboardingScreen(
                            onContinue = { showOnboarding = false },
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        HomeScreen(
                            onOpenPermissions = { showOnboarding = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}
