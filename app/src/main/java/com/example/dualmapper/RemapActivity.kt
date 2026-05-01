package com.example.dualmapper

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.dualmapper.ui.remap.RemapScreen
import com.example.dualmapper.ui.remap.RemapViewModel
import com.example.dualmapper.ui.theme.DualGameMapperTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RemapActivity : ComponentActivity() {

    private val viewModel: RemapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DualGameMapperTheme {
                RemapScreen(viewModel = viewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            viewModel.onKeyEvent(event.keyCode)
        }
        return super.dispatchKeyEvent(event)
    }
}