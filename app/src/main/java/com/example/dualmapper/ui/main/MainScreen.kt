package com.example.dualmapper.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.dualmapper.BuildConfig
import com.example.dualmapper.R
import com.example.dualmapper.manager.connection.ConnectionState
import com.example.dualmapper.manager.connection.ConnectionType
import com.example.dualmapper.manager.connection.DeviceInfo
import com.example.dualmapper.service.FloatingService
import com.example.dualmapper.service.MappedAccessibilityService
import com.example.dualmapper.util.AccessibilityHelper
import com.example.dualmapper.util.OverlayPermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    navController: NavController
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val presets by viewModel.presets.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            PermissionStatusCard(
                isOverlayEnabled = OverlayPermissionHelper.canDrawOverlays(context),
                isAccessibilityEnabled = AccessibilityHelper.isServiceEnabled(
                    context,
                    MappedAccessibilityService::class.java
                )
            )
        }

        item {
            Button(
                onClick = {
                    if (OverlayPermissionHelper.canDrawOverlays(context)) {
                        context.startService(Intent(context, FloatingService::class.java))
                    } else {
                        (context as? android.app.Activity)?.let { activity ->
                            OverlayPermissionHelper.requestOverlayPermission(activity, 1001)
                        }
                    }
                },
                enabled = OverlayPermissionHelper.canDrawOverlays(context),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.start_floating_service))
            }
        }

        item {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_accessibility_settings))
            }
        }

        if (AccessibilityHelper.isServiceEnabled(context, MappedAccessibilityService::class.java)) {
            item {
                Button(
                    onClick = { context.stopService(Intent(context, FloatingService::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.stop_floating_service))
                }
            }
        }

        item { HorizontalDivider() }

        item {
            PresetSection(viewModel, presets)
        }

        item { HorizontalDivider() }

        item {
            ImportExportSection(viewModel)
        }

        item { HorizontalDivider() }

        item {
            ConnectionSection(
                connectionState = uiState.connectionState,
                onConnectTypeSelected = { type -> viewModel.onEvent(MainEvent.SelectConnectionType(type)) },
                discoveredDevices = uiState.discoveredDevices,
                onDeviceClick = { device -> viewModel.onEvent(MainEvent.ConnectToDevice(device)) },
                onStartDiscovery = { viewModel.onEvent(MainEvent.StartDiscovery) },
                onStartHost = { viewModel.onEvent(MainEvent.StartHost) },
                onJoinRemote = { address -> viewModel.onEvent(MainEvent.JoinRemote(address)) },
                onResetToken = { viewModel.onEvent(MainEvent.ResetRemoteAuthToken) },
                remoteHostInfo = uiState.remoteHostInfo
            )
        }

        item { HorizontalDivider() }

        item {
            KeyMappingSwitch(
                enabled = uiState.isKeyMappingEnabled,
                onToggle = { viewModel.onEvent(MainEvent.ToggleKeyMapping) }
            )
        }

        item {
            RemoteMappingSwitch(
                enabled = uiState.isRemoteMappingEnabled,
                onToggle = { viewModel.onEvent(MainEvent.ToggleRemoteMapping) }
            )
        }

        item { HorizontalDivider() }

        item {
            Button(
                onClick = { viewModel.onEvent(MainEvent.ResetFloatingPosition) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reset_position))
            }
        }

        item {
            Button(
                onClick = { navController.navigate("help") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.help_title))
            }
        }

        if (BuildConfig.DEBUG) {
            item { HorizontalDivider() }
            item {
                DeveloperOptionsSection()
            }
        }

        if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.usage_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HorizontalDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun PermissionStatusCard(isOverlayEnabled: Boolean, isAccessibilityEnabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.permission_status), style = MaterialTheme.typography.titleMedium)
            Text("${stringResource(R.string.overlay_permission)}: ${if (isOverlayEnabled) stringResource(R.string.granted) else stringResource(R.string.not_granted)}")
            Text("${stringResource(R.string.accessibility_service)}: ${if (isAccessibilityEnabled) stringResource(R.string.enabled) else stringResource(R.string.not_enabled)}")
        }
    }
}

@Composable
fun PresetSection(viewModel: MainViewModel, presets: List<com.example.dualmapper.data.PresetLayoutEntity>) {
    val context = LocalContext.current
    var showPresetDialog by remember { mutableStateOf(false) }

    Text(stringResource(R.string.layout_presets), style = MaterialTheme.typography.titleMedium)
    Button(
        onClick = { showPresetDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.load_preset))
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text(stringResource(R.string.load_preset)) },
            text = {
                Column {
                    presets.forEach { preset ->
                        TextButton(
                            onClick = {
                                viewModel.onEvent(MainEvent.ApplyPreset(preset.id))
                                showPresetDialog = false
                                Toast.makeText(context, context.getString(R.string.preset_applied, preset.name), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(preset.name + if (preset.description != null) " - ${preset.description}" else "")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Button(
        onClick = { viewModel.onEvent(MainEvent.RestoreBackup) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.restore_backup))
    }
}

@Composable
fun ImportExportSection(viewModel: MainViewModel) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportLayoutToUri(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importLayout(it) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { exportLauncher.launch("dualmapper_layout.json") }) {
            Text(stringResource(R.string.export_layout))
        }
        Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
            Text(stringResource(R.string.import_layout))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSection(
    connectionState: ConnectionState,
    onConnectTypeSelected: (ConnectionType) -> Unit,
    discoveredDevices: List<DeviceInfo>,
    onDeviceClick: (DeviceInfo) -> Unit,
    onStartDiscovery: () -> Unit,
    onStartHost: () -> Unit,
    onJoinRemote: (String) -> Unit,
    onResetToken: () -> Unit,
    remoteHostInfo: String?
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(ConnectionType.WIFI_DIRECT) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // 重置确认对话框
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text(stringResource(R.string.reset_token_confirm_title)) },
            text = { Text(stringResource(R.string.reset_token_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetToken()
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Text(stringResource(R.string.connection_type), style = MaterialTheme.typography.titleMedium)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = when (selectedType) {
                ConnectionType.BLUETOOTH -> stringResource(R.string.bluetooth)
                ConnectionType.WIFI_DIRECT -> stringResource(R.string.wifi_direct)
                ConnectionType.LAN -> stringResource(R.string.lan)
                ConnectionType.REMOTE -> stringResource(R.string.remote)
            },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.textFieldColors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf(ConnectionType.WIFI_DIRECT, ConnectionType.BLUETOOTH, ConnectionType.LAN, ConnectionType.REMOTE).forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(when (type) {
                            ConnectionType.BLUETOOTH -> stringResource(R.string.bluetooth)
                            ConnectionType.WIFI_DIRECT -> stringResource(R.string.wifi_direct)
                            ConnectionType.LAN -> stringResource(R.string.lan)
                            ConnectionType.REMOTE -> stringResource(R.string.remote)
                        })
                    },
                    onClick = {
                        selectedType = type
                        expanded = false
                        onConnectTypeSelected(type)
                    }
                )
            }
        }
    }
    Text("${stringResource(R.string.connection_status)}: $connectionState", color = MaterialTheme.colorScheme.onSurface)

    if (selectedType == ConnectionType.REMOTE) {
        Button(
            onClick = onStartHost,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.create_host))
        }

        if (!remoteHostInfo.isNullOrEmpty()) {
            Text(
                text = remoteHostInfo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        var remoteAddress by remember { mutableStateOf("") }
        OutlinedTextField(
            value = remoteAddress,
            onValueChange = { remoteAddress = it },
            label = { Text(stringResource(R.string.enter_remote_address)) },
            placeholder = { Text(stringResource(R.string.example_address)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onJoinRemote(remoteAddress) },
            enabled = remoteAddress.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.join_remote))
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showResetConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.reset_token_confirm_title))
        }
    } else {
        Button(onClick = onStartDiscovery, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.refresh_devices))
        }

        if (discoveredDevices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                discoveredDevices.forEach { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onDeviceClick(device) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(device.name)
                            Text(stringResource(R.string.tap_to_connect), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            Text(stringResource(R.string.no_devices_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun KeyMappingSwitch(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.key_mapping_switch))
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
fun RemoteMappingSwitch(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.remote_mapping_mode))
        Switch(checked = enabled, onCheckedChange = { onToggle() })
    }
}

@Composable
fun DeveloperOptionsSection() {
    val context = LocalContext.current
    var showKeySimulator by remember { mutableStateOf(false) }
    Column {
        Text("开发者工具", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { showKeySimulator = !showKeySimulator },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("模拟按键发送")
        }
        if (showKeySimulator) {
            KeySimulatorPanel()
        }
        Button(
            onClick = {
                val logFile = com.example.dualmapper.util.LogExporter.exportLogs(context)
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.dualmapper.fileprovider", logFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "导出日志"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("导出调试日志")
        }
    }
}

@Composable
fun KeySimulatorPanel() {
    val context = LocalContext.current
    val keyCodes = listOf(
        29 to stringResource(R.string.sim_key_a),
        30 to stringResource(R.string.sim_key_b),
        51 to stringResource(R.string.sim_key_x),
        52 to stringResource(R.string.sim_key_y),
        19 to stringResource(R.string.sim_key_up),
        20 to stringResource(R.string.sim_key_down),
        21 to stringResource(R.string.sim_key_left),
        22 to stringResource(R.string.sim_key_right),
        66 to stringResource(R.string.sim_key_confirm)
    )
    Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
        keyCodes.forEach { (code, label) ->
            Button(
                onClick = {
                    val intent = Intent("com.example.dualmapper.SIMULATE_KEY").apply { putExtra("keyCode", code) }
                    context.sendBroadcast(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Text(label)
            }
        }
    }
}