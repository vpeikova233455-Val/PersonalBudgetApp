package com.budgetapp.presentation.update

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateDialog(state: UpdateState, viewModel: UpdateViewModel) {
    when (state) {
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdate,
                title = { Text("Update available — v${state.info.latestVersion}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.info.releaseNotes.isNotBlank()) {
                            Text(
                                state.info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Tap Update to download and install automatically.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::startDownload) { Text("Update") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdate) { Text("Later") }
                }
            )
        }

        is UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading v${state.info.latestVersion}…") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${state.progress}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is UpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdate,
                title = { Text("Ready to install") },
                text = {
                    Text("The installer has launched. Follow the on-screen steps to complete the update.")
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissUpdate) { Text("OK") }
                }
            )
        }

        is UpdateState.Failed -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdate,
                title = { Text("Update failed") },
                text = { Text(state.reason) },
                confirmButton = {
                    Button(onClick = viewModel::retryDownload) { Text("Retry") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdate) { Text("Cancel") }
                }
            )
        }

        UpdateState.Idle -> {}
    }
}
