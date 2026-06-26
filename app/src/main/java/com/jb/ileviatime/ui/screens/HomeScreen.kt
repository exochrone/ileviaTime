package com.jb.ileviatime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jb.ileviatime.R
import com.jb.ileviatime.domain.model.DataStatus
import com.jb.ileviatime.ui.formatter.DisplayFormatter
import com.jb.ileviatime.ui.viewmodel.HomeViewModel
import com.jb.ileviatime.ui.viewmodel.PinnedTripUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTrip: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val pinnedTrips by viewModel.pinnedTripsState.collectAsStateWithLifecycle()
    val nowSeconds = System.currentTimeMillis() / 1000

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
                    PinnedTripCard(
                        state = state,
                        nowSeconds = nowSeconds,
                        onDelete = { viewModel.deletePinnedTrip(state.pinnedTrip) },
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}

@Composable
fun PinnedTripCard(
    state: PinnedTripUiState,
    nowSeconds: Long,
    onDelete: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Ligne ${state.pinnedTrip.routeId}", style = MaterialTheme.typography.titleMedium)
                    Text(text = "${state.departureStopName} → ${state.arrivalStopName}", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            state.passages.forEach { passage ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = DisplayFormatter.formatPassageTime(passage.departure, nowSeconds))
                    Text(text = "→")
                    Text(text = DisplayFormatter.formatPassageTime(passage.arrival, nowSeconds))
                }
            }

            if (state.dataStatus is DataStatus.Stale) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.data_stale, 
                            DisplayFormatter.formatTime(state.dataStatus.lastUpdateAt)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
