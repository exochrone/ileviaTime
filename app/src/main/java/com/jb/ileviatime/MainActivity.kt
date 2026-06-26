package com.jb.ileviatime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jb.ileviatime.ui.screens.LoadingScreen
import com.jb.ileviatime.ui.screens.MainScreen
import com.jb.ileviatime.ui.theme.IleviaTimeTheme
import com.jb.ileviatime.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IleviaTimeTheme {
                val viewModel: MainViewModel = viewModel()
                val isDataReady by viewModel.isDataReady.collectAsState()
                val isSyncing by viewModel.isSyncing.collectAsState()
                val syncError by viewModel.syncError.collectAsState()

                if (!isDataReady || isSyncing || syncError != null) {
                    LoadingScreen(
                        error = syncError,
                        onRetry = { viewModel.retrySync() }
                    )
                } else {
                    MainScreen()
                }
            }
        }
    }
}
