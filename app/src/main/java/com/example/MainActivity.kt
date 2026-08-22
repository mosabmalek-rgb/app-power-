package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.FieldReadingViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        // Enforce Arabic RTL direction globally
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
          ) {
            val navController = rememberNavController()
            val viewModel: FieldReadingViewModel = viewModel()
            
            val startDestination = if (viewModel.isLoggedIn.value) "dashboard" else "login"

            NavHost(
              navController = navController,
              startDestination = startDestination
            ) {
              composable("login") {
                LoginScreen(viewModel = viewModel) {
                  navController.navigate("dashboard") {
                    popUpTo("login") { inclusive = true }
                  }
                }
              }

              composable("dashboard") {
                DashboardScreen(
                  viewModel = viewModel,
                  onNavigateToSubscribers = { navController.navigate("subscribers") },
                  onNavigateToSync = { navController.navigate("sync") },
                  onNavigateToSettings = { navController.navigate("settings") }
                )
              }

              composable("subscribers") {
                SubscribersScreen(
                  viewModel = viewModel,
                  onNavigateToDetails = { subscriberId ->
                    navController.navigate("subscriber_detail")
                  },
                  onBack = { navController.popBackStack() }
                )
              }

              composable("subscriber_detail") {
                SubscriberDetailScreen(
                  viewModel = viewModel,
                  onNavigateToAddReading = { navController.navigate("add_reading") },
                  onBack = { navController.popBackStack() }
                )
              }

              composable("add_reading") {
                AddReadingScreen(
                  viewModel = viewModel,
                  onBack = { navController.popBackStack() }
                )
              }

              composable("sync") {
                SyncScreen(
                  viewModel = viewModel,
                  onBack = { navController.popBackStack() }
                )
              }

              composable("settings") {
                SettingsScreen(
                  viewModel = viewModel,
                  onLogout = {
                    navController.navigate("login") {
                      popUpTo("dashboard") { inclusive = true }
                    }
                  },
                  onBack = { navController.popBackStack() }
                )
              }
            }
          }
        }
      }
    }
  }
}
