package com.jb.ileviatime.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jb.ileviatime.R
import com.jb.ileviatime.ui.viewmodel.TripCreationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCreationScreen(
    onBack: () -> Unit,
    viewModel: TripCreationViewModel = hiltViewModel()
) {
    val modes = viewModel.modes
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val selectedRoute by viewModel.selectedRoute.collectAsStateWithLifecycle()
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val selectedDepartureStop by viewModel.selectedDepartureStop.collectAsStateWithLifecycle()
    val selectedArrivalStop by viewModel.selectedArrivalStop.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_trip)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode
            AppDropdown(
                label = stringResource(R.string.select_mode),
                options = modes,
                selectedOption = selectedMode,
                onOptionSelected = { viewModel.selectMode(it) }
            )

            // Route
            AppDropdown(
                label = stringResource(R.string.select_line),
                options = routes.map { it.routeShortName },
                selectedOption = selectedRoute?.routeShortName,
                onOptionSelected = { name ->
                    routes.find { it.routeShortName == name }?.let { viewModel.selectRoute(it) }
                },
                enabled = selectedMode != null
            )

            // Departure
            AppDropdown(
                label = stringResource(R.string.select_departure),
                options = stops.map { it.stopName },
                selectedOption = selectedDepartureStop?.stopName,
                onOptionSelected = { name ->
                    stops.find { it.stopName == name }?.let { viewModel.selectDepartureStop(it) }
                },
                enabled = selectedRoute != null
            )

            // Arrival
            AppDropdown(
                label = stringResource(R.string.select_arrival),
                options = stops.map { it.stopName }.filter { it != selectedDepartureStop?.stopName },
                selectedOption = selectedArrivalStop?.stopName,
                onOptionSelected = { name ->
                    stops.find { it.stopName == name }?.let { viewModel.selectArrivalStop(it) }
                },
                enabled = selectedDepartureStop != null
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { 
                    viewModel.pinTrip()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedArrivalStop != null
            ) {
                Text(stringResource(R.string.pin_trip))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdown(
    label: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
