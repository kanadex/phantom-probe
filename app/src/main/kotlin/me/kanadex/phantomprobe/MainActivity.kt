// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.kanadex.phantomprobe.ui.ProbeAction
import me.kanadex.phantomprobe.ui.ProbeScreen
import me.kanadex.phantomprobe.ui.rememberProbeViewModel
import me.kanadex.phantomprobe.ui.theme.PhantomProbeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhantomProbeTheme {
                val viewModel = rememberProbeViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                // Let the enter transition finish before the probe process starts forking.
                // ProbeClient decides whether a run is still needed, so a rotation that keeps
                // this effect from re-firing costs nothing.
                LaunchedEffect(Unit) {
                    delay(300)
                    viewModel.dispatch(ProbeAction.Launch)
                }

                ProbeScreen(
                    state = state,
                    onToggle = { viewModel.dispatch(ProbeAction.Toggle) },
                    onOpenDeveloperOptions = { viewModel.dispatch(ProbeAction.OpenDeveloperOptions) },
                )
            }
        }
    }
}
