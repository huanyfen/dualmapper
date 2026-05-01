package com.example.dualmapper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.dualmapper.ui.KeySettingsDialog
import com.example.dualmapper.ui.theme.DualGameMapperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KeySettingsDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyId = intent.getStringExtra("key_id") ?: return finish()
        val initialAlpha = intent.getFloatExtra("alpha", 1f)

        setContent {
            DualGameMapperTheme {
                var showDialog by remember { mutableStateOf(true) }

                if (showDialog) {
                    KeySettingsDialog(
                        currentAlpha = initialAlpha,
                        onAlphaChanged = { newAlpha ->
                            // 实时通知 FloatingService 更新透明度
                            sendBroadcast(Intent("ACTION_KEY_ALPHA_CHANGED").apply {
                                putExtra("key_id", keyId)
                                putExtra("alpha", newAlpha)
                            })
                        },
                        onDelete = {
                            sendBroadcast(Intent("ACTION_KEY_DELETED").apply {
                                putExtra("key_id", keyId)
                            })
                            showDialog = false
                            finish()
                        },
                        onRemap = {
                            startActivity(Intent(this@KeySettingsDialogActivity, RemapActivity::class.java).apply {
                                putExtra("key_id", keyId)
                            })
                            showDialog = false
                            finish()
                        },
                        onChangeIcon = {
                            sendBroadcast(Intent("ACTION_REQUEST_PICK_ICON").apply {
                                putExtra("keyId", keyId)
                            })
                            showDialog = false
                            finish()
                        },
                        onDismiss = {
                            showDialog = false
                            finish()
                        }
                    )
                }
            }
        }
    }
}