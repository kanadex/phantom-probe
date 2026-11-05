// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 InstallerX Revived contributors
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui.widget

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

internal class ListItemMotionScheme(delegate: MotionScheme) : MotionScheme by delegate {
    // Material 3 1.5.0-beta01 interpolates corner radii with an unbounded fast-spatial spec.
    // A spring can overshoot past a zero radius, including when a press is reversed; a tween
    // ignores the incoming velocity and keeps the shape between its valid endpoints.
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 180)
}

@Composable
internal fun ListItemMotion(content: @Composable () -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    val listItemMotionScheme = remember(motionScheme) { ListItemMotionScheme(motionScheme) }
    MaterialTheme(motionScheme = listItemMotionScheme, content = content)
}
