// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 InstallerX Revived contributors
// Copyright (C) 2026 kanadex
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package me.kanadex.phantomprobe.ui.widget

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.kanadex.phantomprobe.ui.theme.ConnectionRadius
import me.kanadex.phantomprobe.ui.theme.CornerRadius
import kotlin.math.roundToInt

private const val PADDING_HORIZONTAL = 16
private const val PADDING_VERTICAL = 8

private const val BOUNCY_STIFFNESS = 800f
private const val BOUNCY_DAMPING = 0.5f

@DslMarker
annotation class SegmentedColumnDsl

/**
 * One row of a [SegmentedColumn].
 *
 * @property key stable identity across recompositions.
 * @property visible target visibility; transitions drive the enter and exit animations.
 * A row that must never be visible should not be registered at all.
 * @property customTopPadding optional per-row padding above the row.
 * @property forceFlatTop forces the top corner radius to zero.
 * @property forceFlatBottom forces the bottom corner radius to zero.
 * @property content receives the shape computed for this row's position in the group.
 */
@Immutable
data class SegmentedItemData(
    val key: Any?,
    val visible: Boolean,
    val customTopPadding: Dp? = null,
    val forceFlatTop: Boolean = false,
    val forceFlatBottom: Boolean = false,
    val content: @Composable (Shape) -> Unit,
)

/** The DSL scope used to declare the rows of a [SegmentedColumn]. */
@SegmentedColumnDsl
class SegmentedColumnScope {

    val items = mutableListOf<SegmentedItemData>()

    /**
     * Registers a row.
     *
     * Use [animatedVisibility] only for rows that appear and disappear at runtime; a row that
     * is permanently hidden should be wrapped in an `if` in the caller instead.
     */
    fun item(
        key: Any? = null,
        animatedVisibility: Boolean = true,
        topPadding: Dp? = null,
        forceFlatTop: Boolean = false,
        forceFlatBottom: Boolean = false,
        content: @Composable (Shape) -> Unit,
    ) {
        items.add(
            SegmentedItemData(
                key = key ?: items.size,
                visible = animatedVisibility,
                customTopPadding = topPadding,
                forceFlatTop = forceFlatTop,
                forceFlatBottom = forceFlatBottom,
                content = content,
            ),
        )
    }

    /**
     * Registers an expandable pair: a header that stays visible and a body that follows it.
     * The seam between the two loses its rounded corners while the body is expanded, so the
     * pair reads as one container.
     */
    fun expandableItem(
        animatedVisibility: Boolean = true,
        expanded: Boolean,
        topPadding: Dp? = null,
        bottomPadding: Dp = 1.dp,
        topContent: @Composable (Shape) -> Unit,
        bottomContent: @Composable (Shape) -> Unit,
    ) {
        item(
            animatedVisibility = animatedVisibility,
            topPadding = topPadding,
            forceFlatBottom = expanded,
            content = topContent,
        )
        item(
            animatedVisibility = animatedVisibility && expanded,
            topPadding = bottomPadding,
            forceFlatTop = true,
            content = bottomContent,
        )
    }
}

/**
 * A vertical group that splices its rows together: 16 dp on the group edges, 5 dp on the seams,
 * 2 dp of gap between rows, and animated corners while rows enter or leave.
 *
 * Adapted from InstallerX Revived's `SegmentedColumn` (GPL-3.0). The row shapes are computed
 * here and handed down through [LocalSegmentedItemShape]; rows never compute a radius.
 *
 * @param title optional section title, drawn with the group's own inset.
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    title: String = "",
    contentPadding: PaddingValues = PaddingValues(
        horizontal = PADDING_HORIZONTAL.dp,
        vertical = PADDING_VERTICAL.dp,
    ),
    content: SegmentedColumnScope.() -> Unit,
) {
    val scope = SegmentedColumnScope().apply(content)
    val allItems = scope.items

    if (allItems.isEmpty()) return

    Column(modifier = modifier.padding(contentPadding)) {
        if (title.isNotEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    start = PADDING_HORIZONTAL.dp,
                    top = PADDING_VERTICAL.dp,
                    bottom = 16.dp,
                ),
            )
        }

        val floatSpring = spring<Float>(dampingRatio = BOUNCY_DAMPING, stiffness = BOUNCY_STIFFNESS)
        val dpSpring = spring<Dp>(dampingRatio = BOUNCY_DAMPING, stiffness = BOUNCY_STIFFNESS)

        val progresses = allItems.mapIndexed { index, item ->
            key(item.key ?: index) {
                animateFloatAsState(
                    targetValue = if (item.visible) 1f else 0f,
                    animationSpec = floatSpring,
                    label = "progress",
                )
            }
        }

        val firstVisibleIndex = allItems.indexOfFirst { it.visible }
        val lastVisibleIndex = allItems.indexOfLast { it.visible }

        val focusManager = LocalFocusManager.current

        Layout(
            content = {
                allItems.forEachIndexed { index, itemData ->
                    key(itemData.key ?: index) {
                        val isFirst = index == firstVisibleIndex || (index == 0 && !itemData.visible)
                        val isLast = index == lastVisibleIndex ||
                            (index == allItems.lastIndex && !itemData.visible)

                        val baseTopRadius = if (isFirst) CornerRadius else ConnectionRadius
                        val baseBottomRadius = if (isLast) CornerRadius else ConnectionRadius

                        val targetTopRadius = if (itemData.forceFlatTop) 0.dp else baseTopRadius
                        val targetBottomRadius =
                            if (itemData.forceFlatBottom) 0.dp else baseBottomRadius

                        // Animated Dp only exists on Android 13+; earlier versions switch shape
                        // without interpolating, which is acceptable for a static group.
                        val animateDp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

                        val currentTopRadius = (
                            if (animateDp) {
                                animateDpAsState(targetTopRadius, dpSpring, label = "TopRadius").value
                            } else {
                                targetTopRadius
                            }
                            ).coerceAtLeast(0.dp)

                        val currentBottomRadius = (
                            if (animateDp) {
                                animateDpAsState(targetBottomRadius, dpSpring, label = "BottomRadius").value
                            } else {
                                targetBottomRadius
                            }
                            ).coerceAtLeast(0.dp)

                        val shape = RoundedCornerShape(
                            topStart = currentTopRadius,
                            topEnd = currentTopRadius,
                            bottomStart = currentBottomRadius,
                            bottomEnd = currentBottomRadius,
                        )

                        val targetTopPadding = itemData.customTopPadding
                            ?: (if (isFirst) 0.dp else ListItemDefaults.SegmentedGap)
                        val currentTopPadding = (
                            if (animateDp) {
                                animateDpAsState(targetTopPadding, dpSpring, label = "TopPadding").value
                            } else {
                                targetTopPadding
                            }
                            ).coerceAtLeast(0.dp)

                        var hasFocus by remember { mutableStateOf(false) }

                        LaunchedEffect(itemData.visible) {
                            if (!itemData.visible && hasFocus) focusManager.clearFocus()
                        }

                        Box(
                            modifier = Modifier
                                .zIndex(
                                    if (itemData.visible) {
                                        (allItems.size - index).toFloat()
                                    } else {
                                        -index.toFloat()
                                    },
                                )
                                .onFocusChanged { hasFocus = it.hasFocus }
                                .semantics {
                                    if (!itemData.visible) hideFromAccessibility()
                                }
                                .graphicsLayer {
                                    // Read animation state in the draw phase so a running reveal
                                    // does not cause a recomposition per frame.
                                    val progress = progresses[index].value
                                    val safeProgress = progress.coerceIn(0f, 1f)
                                    val reveal = safeProgress * safeProgress * (3f - 2f * safeProgress)

                                    clip = true
                                    this.shape = object : Shape {
                                        override fun createOutline(
                                            size: Size,
                                            layoutDirection: LayoutDirection,
                                            density: Density,
                                        ) = Outline.Rectangle(
                                            Rect(0f, 0f, size.width, size.height * reveal),
                                        )
                                    }
                                    alpha = (progress * 1.5f).coerceIn(0f, 1f)
                                },
                        ) {
                            CompositionLocalProvider(LocalSegmentedItemShape provides shape) {
                                Column(modifier = Modifier.padding(top = currentTopPadding)) {
                                    itemData.content(shape)
                                }
                            }
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val advances = segmentedHeightAdvances(
                heights = FloatArray(placeables.size) { placeables[it].height.toFloat() },
                progresses = FloatArray(placeables.size) { progresses[it].value },
                reboundLimit = 24.dp.toPx(),
            )
            var y = 0f
            val positions = IntArray(placeables.size)
            placeables.indices.forEach { index ->
                positions[index] = y.roundToInt()
                y += advances[index]
            }
            layout(constraints.maxWidth, y.roundToInt().coerceAtLeast(0)) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(x = 0, y = positions[index])
                }
            }
        }
    }
}
