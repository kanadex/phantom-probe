// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The single source of the segmented-list geometry. Group edges use [CornerRadius], the seam
 * between two rows of the same group uses [ConnectionRadius]; nothing else may hardcode either.
 */
val CornerRadius = 16.dp
val ConnectionRadius = 5.dp

val topShape = RoundedCornerShape(
    topStart = CornerRadius,
    topEnd = CornerRadius,
    bottomStart = ConnectionRadius,
    bottomEnd = ConnectionRadius,
)

val middleShape = RoundedCornerShape(ConnectionRadius)

val bottomShape = RoundedCornerShape(
    topStart = ConnectionRadius,
    topEnd = ConnectionRadius,
    bottomStart = CornerRadius,
    bottomEnd = CornerRadius,
)

val singleShape = RoundedCornerShape(CornerRadius)
