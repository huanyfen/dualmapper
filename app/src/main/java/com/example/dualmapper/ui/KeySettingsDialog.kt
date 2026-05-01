package com.example.dualmapper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.dualmapper.R

@Composable
fun KeySettingsDialog(
    currentAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
    onDelete: () -> Unit,
    onRemap: () -> Unit,
    onChangeIcon: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var alpha by remember { mutableFloatStateOf(currentAlpha) }

    AlertDialog(
        onDismissRequest = {
            onAlphaChanged(currentAlpha) // revert on cancel
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.key_settings),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.opacity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = alpha,
                    onValueChange = { newAlpha ->
                        alpha = newAlpha
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onChangeIcon != null) {
                    Button(
                        onClick = onChangeIcon,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.change_icon))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAlphaChanged(alpha)   // apply on confirm
                onRemap()
                onDismiss()
            }) {
                Text(stringResource(R.string.remap))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onAlphaChanged(currentAlpha)  // cancel
                onDelete()
                onDismiss()
            }) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}