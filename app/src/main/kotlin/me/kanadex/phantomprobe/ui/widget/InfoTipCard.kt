// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A tinted hint band: 16 dp corners, tertiary container colours, one 24 dp icon and one
 * paragraph. Used for the text that explains the cap and for the footnote, which is why the
 * caller passes a single already-joined string.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InfoTipCard(
    text: String,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    icon: ImageVector? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMediumEmphasized,
            )
        }
    }
}
