// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui

/**
 * Everything the screen draws, already resolved to text.
 *
 * The screen renders these fields and nothing else: no phase arithmetic, no pid formatting and
 * no clock reading happens in a composable. Defaults are empty so previews and tests can build
 * a state without touching the probe.
 */
data class ProbeUiState(
    val title: String = "",
    val detail: String = "",
    val actionText: String = "",
    val ok: Boolean = true,
    val mainDetail: String = "",
    val phantomDetail: String = "",
    val handshakeDetail: String = "",
    val forkDetail: String = "",
    val pokeDetail: String = "",
    val settleDetail: String = "",
    val observeDetail: String = "",
)

/** The only inputs the probe screen accepts. */
sealed interface ProbeAction {

    /** Status card tap: run a probe, or stop the run that is in flight. */
    data object Toggle : ProbeAction

    /** Screen entry: probe once per process unless a verdict already exists. */
    data object Launch : ProbeAction

    /** Open the system developer options page. */
    data object OpenDeveloperOptions : ProbeAction
}
