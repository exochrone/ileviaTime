package com.jb.ileviatime.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onAddTrip = { navController.navigate("create_trip") })
        }
        composable("create_trip") {
            TripCreationScreen(onBack = { navController.popBackStack() })
        }
    }
}
