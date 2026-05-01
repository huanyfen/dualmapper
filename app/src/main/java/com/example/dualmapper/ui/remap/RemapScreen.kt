package com.example.dualmapper.ui.remap

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dualmapper.R
import kotlinx.coroutines.launch

@Composable
fun RemapScreen(
    viewModel: RemapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var boxPosition by remember { mutableStateOf(Offset.Zero) }
    val currentBoxPosition by rememberUpdatedState(boxPosition)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.record_mapping),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = stringResource(R.string.current_action, uiState.actionType),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionTypeChip("tap", uiState.actionType, viewModel::setActionType)
            ActionTypeChip("swipe", uiState.actionType, viewModel::setActionType)
            ActionTypeChip("longpress", uiState.actionType, viewModel::setActionType)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 修复：添加玩家选择（P1 / P2）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.player_select),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(12.dp))
            PlayerChip(1, uiState.playerIndex) { viewModel.setPlayerIndex(it) }
            Spacer(modifier = Modifier.width(8.dp))
            PlayerChip(2, uiState.playerIndex) { viewModel.setPlayerIndex(it) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = uiState.instruction,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "坐标: (${uiState.currentTouchX}, ${uiState.currentTouchY})",
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInRoot()
                    boxPosition = Offset(pos.x, pos.y)
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val screenOffset = Offset(
                            offset.x + currentBoxPosition.x,
                            offset.y + currentBoxPosition.y
                        )
                        viewModel.onTouchEvent(screenOffset)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val screenOffset = Offset(
                                offset.x + currentBoxPosition.x,
                                offset.y + currentBoxPosition.y
                            )
                            viewModel.onDragStart(screenOffset)
                        },
                        onDragEnd = { viewModel.onDragEnd() },
                        onDragCancel = { }
                    ) { change, _ ->
                        change.consume()
                        val screenOffset = Offset(
                            change.position.x + currentBoxPosition.x,
                            change.position.y + currentBoxPosition.y
                        )
                        viewModel.onDrag(screenOffset)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                uiState.startPoint?.let { start ->
                    val localStart = Offset(start.x - boxPosition.x, start.y - boxPosition.y)
                    uiState.endPoint?.let { end ->
                        val localEnd = Offset(end.x - boxPosition.x, end.y - boxPosition.y)
                        drawLine(
                            color = MaterialTheme.colorScheme.primary,
                            start = localStart,
                            end = localEnd,
                            strokeWidth = 8f
                        )
                    } ?: run {
                        drawCircle(
                            color = MaterialTheme.colorScheme.primary,
                            radius = 20f,
                            center = localStart
                        )
                    }
                }
            }

            if (uiState.startPoint == null) {
                Text(
                    text = stringResource(R.string.touch_here_to_record),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { viewModel.startKeyRecord() }) {
                Text(stringResource(R.string.record_key))
            }
            Text(
                text = uiState.recordedKeyLabel ?: stringResource(R.string.no_key_recorded),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.saveMapping()
                        (context as? Activity)?.finish()
                    }
                },
                enabled = uiState.canSave,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
fun ActionTypeChip(type: String, currentType: String, onSelect: (String) -> Unit) {
    val label = when (type) {
        "tap" -> stringResource(R.string.tap)
        "swipe" -> stringResource(R.string.swipe)
        "longpress" -> stringResource(R.string.long_press)
        else -> type
    }
    FilterChip(
        selected = type == currentType,
        onClick = { onSelect(type) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

// 修复：玩家选择器 Composable
@Composable
fun PlayerChip(player: Int, currentPlayer: Int, onSelect: (Int) -> Unit) {
    val label = when (player) {
        1 -> stringResource(R.string.player_1)
        2 -> stringResource(R.string.player_2)
        else -> "P$player"
    }
    FilterChip(
        selected = player == currentPlayer,
        onClick = { onSelect(player) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}