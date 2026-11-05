// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 InstallerX Revived contributors
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui.widget

import kotlin.math.expm1

/**
 * Non-negative layout advances with a single, soft rebound budget for the whole group.
 *
 * Smoothstep keeps velocity at zero on both endpoints. Past either endpoint the quadratic
 * displacement joins with zero slope, so a spring overshoot still produces a small rebound
 * instead of a hard clamp or a negative height that would pull later rows backwards.
 */
internal fun segmentedHeightAdvances(
    heights: FloatArray,
    progresses: FloatArray,
    reboundLimit: Float,
): FloatArray {
    require(heights.size == progresses.size)
    require(reboundLimit > 0f)
    val overflow = FloatArray(heights.size)
    val advances = FloatArray(heights.size)
    var totalOverflow = 0.0
    heights.indices.forEach { index ->
        val height = heights[index]
        val progress = progresses[index]
        val within = progress.coerceIn(0f, 1f)
        advances[index] = height * within * within * (3f - 2f * within)
        val beyond = progress - within
        overflow[index] = height * 3f * beyond * beyond
        totalOverflow += overflow[index]
    }
    if (totalOverflow > 0.0) {
        // expm1 keeps precision for the tiny displacements near the endpoint.
        val rebound = -reboundLimit * expm1(-totalOverflow / reboundLimit)
        val scale = (rebound / totalOverflow).toFloat()
        advances.indices.forEach { advances[it] += overflow[it] * scale }
    }
    return advances
}
