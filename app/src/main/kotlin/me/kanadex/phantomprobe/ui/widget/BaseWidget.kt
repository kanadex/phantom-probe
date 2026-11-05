// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2023-2026 InstallerX Revived contributors
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui.widget

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.kanadex.phantomprobe.ui.theme.CornerRadius

/**
 * The shape of the row currently being composed inside a [SegmentedColumn]. Rows read it to
 * follow the group edges without computing any radius themselves.
 */
val LocalSegmentedItemShape = compositionLocalOf<Shape> { RoundedCornerShape(CornerRadius) }

/**
 * The single row primitive: icon slot, headline, supporting text, trailing slot, and the
 * 56/72 dp minimum height that Material 3 list items use.
 *
 * Adapted from InstallerX Revived's `BaseWidget` (GPL-3.0) with the same layout rules; the
 * font-scale workaround below is kept because Material 3 list items still do not shrink with
 * the system font size.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BaseWidget(
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color? = null,
    iconPlaceholder: Boolean = true,
    title: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    description: String? = null,
    descriptionStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    descriptionColor: Color? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onTrailingClick: (() -> Unit)? = null,
    clickHaptic: HapticFeedbackType? = HapticFeedbackType.VirtualKey,
    trailingDivider: Boolean = false,
    foreContent: @Composable BoxScope.() -> Unit = {},
    trailingContent: @Composable BoxScope.(interactionSource: MutableInteractionSource) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val alpha = if (enabled) 1f else 0.38f

    val interactionSource = remember { MutableInteractionSource() }
    val trailingInteractionSource = remember { MutableInteractionSource() }
    val trailingContentInteractionSource =
        if (onTrailingClick != null || trailingDivider) trailingInteractionSource else interactionSource

    val handleTrailingClick = onTrailingClick?.let { callback ->
        {
            clickHaptic?.let { haptic.performHapticFeedback(it) }
            callback()
        }
    }

    val fontScale = LocalDensity.current.fontScale
    val defaultMinHeight = if (description == null) 56.dp else 72.dp
    val adaptiveMinHeight = (defaultMinHeight * fontScale).coerceAtLeast(48.dp)

    val baseShape = LocalSegmentedItemShape.current

    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

    val baseContentColor = if (selected) {
        MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.primaryContainer)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val resolvedIconColor = iconColor
        ?: if (selected) {
            baseContentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    val finalDescriptionColor = when {
        isError -> MaterialTheme.colorScheme.error
        descriptionColor != null -> descriptionColor
        else -> baseContentColor.copy(alpha = 0.7f)
    }

    /*
     * Disabled colours keep their own alpha here: disabled opacity is applied with
     * Modifier.alpha around each slot, so it is never applied twice and the non-clickable
     * variant looks exactly like the clickable one.
     */
    val colors = ListItemDefaults.colors(
        containerColor = backgroundColor,
        contentColor = baseContentColor,
        leadingContentColor = resolvedIconColor,
        trailingContentColor = resolvedIconColor,
        supportingContentColor = finalDescriptionColor,
        selectedContainerColor = backgroundColor,
        selectedContentColor = baseContentColor,
        selectedLeadingContentColor = resolvedIconColor,
        selectedTrailingContentColor = resolvedIconColor,
        selectedSupportingContentColor = finalDescriptionColor,
        disabledContainerColor = backgroundColor,
        disabledContentColor = baseContentColor,
        disabledLeadingContentColor = resolvedIconColor,
        disabledTrailingContentColor = resolvedIconColor,
        disabledSupportingContentColor = finalDescriptionColor,
    )

    val shapes = ListItemDefaults.shapes(
        shape = baseShape,
        pressedShape = RoundedCornerShape(CornerRadius),
        selectedShape = baseShape,
        focusedShape = baseShape,
        hoveredShape = baseShape,
    )

    val itemModifier = modifier
        .fillMaxWidth()
        .heightIn(min = adaptiveMinHeight)

    val leadingContent: (@Composable () -> Unit)? =
        if (icon != null || iconPlaceholder) {
            {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(alpha),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = resolvedIconColor,
                        )
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                }
            }
        } else {
            null
        }

    val supportingContent: (@Composable () -> Unit)? =
        description?.let { text ->
            {
                Text(
                    text = text,
                    style = descriptionStyle,
                    modifier = Modifier.alpha(alpha),
                )
            }
        }

    val trailing: @Composable () -> Unit = {
        Row(
            modifier = Modifier.alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trailingDivider) VerticalDivider(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .then(
                        if (handleTrailingClick != null) {
                            Modifier.clickable(
                                enabled = enabled,
                                interactionSource = trailingInteractionSource,
                                indication = LocalIndication.current,
                                onClick = handleTrailingClick,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(start = if (trailingDivider) 16.dp else 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                trailingContent(trailingContentInteractionSource)
            }
        }
    }

    val headline: @Composable () -> Unit = {
        Box(modifier = Modifier.alpha(alpha)) {
            Text(text = title, style = titleStyle)
            foreContent()
        }
    }

    if (onClick != null) {
        ListItemMotion {
            ListItem(
                selected = selected,
                modifier = itemModifier,
                onClick = {
                    clickHaptic?.let { haptic.performHapticFeedback(it) }
                    onClick()
                },
                enabled = enabled,
                colors = colors,
                shapes = shapes,
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                trailingContent = trailing,
                interactionSource = interactionSource,
                content = headline,
            )
        }
    } else {
        /*
         * A null onClick must not become enabled = false: that would advertise the row as
         * disabled and change how screen readers announce it.
         */
        ListItem(
            modifier = itemModifier
                .clip(baseShape)
                .then(
                    if (!enabled) {
                        Modifier.semantics { disabled() }
                    } else {
                        Modifier
                    },
                ),
            colors = colors,
            leadingContent = leadingContent,
            supportingContent = supportingContent,
            trailingContent = trailing,
            content = headline,
        )
    }
}
