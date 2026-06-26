package com.jb.ileviatime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jb.ileviatime.R
import com.jb.ileviatime.ui.viewmodel.HomeViewModel
import com.jb.ileviatime.ui.viewmodel.PinnedTripUiState
import com.jb.ileviatime.domain.model.PassageTime
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTrip: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pinnedTrips by viewModel.pinnedTripsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTrip) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_trip))
            }
        }
    ) { innerPadding ->
        if (pinnedTrips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(text = "Aucun trajet épinglé")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(pinnedTrips, key = { it.pinnedTrip.id }) { state ->
                    PinnedTripCard(state, onDelete = { viewModel.deletePinnedTrip(state.pinnedTrip) })
                }
            }
        }
    }
}

@Composable
fun PinnedTripCard(
    state: PinnedTripUiState,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text = "Ligne \${state.pinnedTrip.routeId}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "\${state.pinnedTrip.departureStopId} → \${state.pinnedTrip.arrivalStopId}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            state.passages.forEach { passage ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = formatPassageTime(passage.departure))
                    Text(text = "→")
                    Text(text = formatPassageTime(passage.arrival))
                }
            }
        }
    }
}

fun formatPassageTime(time: PassageTime): String {
    return when (time) {
        is PassageTime.RealTime -> formatEpoch(time.epochSeconds, isScheduled = false)
        is PassageTime.Scheduled -> formatEpoch(time.epochSeconds, isScheduled = true)
        is PassageTime.NotAvailable -> "N/A"
    }
}

fun formatEpoch(epoch: Long, isScheduled: Boolean): String {
    val now = System.currentTimeMillis() / 1000
    val diffMinutes = (epoch - now) / 60
    
    val timeStr = if (diffMinutes in 0..59) {
        "\$diffMinutes\u0027"
    } else {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(epoch * 1000))
    }
    
    return if (isScheduled) "(\$timeStr)" else timeStr
}
