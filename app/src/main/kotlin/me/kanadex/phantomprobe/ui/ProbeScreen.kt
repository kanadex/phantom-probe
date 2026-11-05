// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kanadex.phantomprobe.R
import me.kanadex.phantomprobe.probe.ProbeProtocol
import me.kanadex.phantomprobe.ui.widget.BaseWidget
import me.kanadex.phantomprobe.ui.widget.InfoTipCard
import me.kanadex.phantomprobe.ui.widget.NavigationItemWidget
import me.kanadex.phantomprobe.ui.widget.SegmentedColumn
import me.kanadex.phantomprobe.ui.widget.StatusCard

/**
 * The probe screen: a verdict card, the cap in one paragraph, the three processes, the four
 * pipeline stages, and the two explanations behind sheets.
 *
 * Every dynamic string arrives through [ProbeUiState]; the static labels in this file are
 * resource ids, which is what keeps the five locales honest.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProbeScreen(
    state: ProbeUiState,
    onToggle: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                title = { Text(stringResource(R.string.app_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                StatusCard(
                    title = state.title,
                    description = state.detail,
                    containerColor = if (state.ok) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (state.ok) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    actionText = state.actionText,
                    icon = if (state.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    onClick = onToggle,
                )
            }

            item {
                InfoTipCard(
                    text = stringResource(
                        R.string.tip_cap,
                        ProbeProtocol.SYSTEM_CAP,
                        ProbeProtocol.FORK_COUNT,
                    ) + "\n" + stringResource(R.string.footnote),
                    icon = Icons.Rounded.Info,
                )
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_processes)) {
                    item {
                        BaseWidget(
                            icon = Icons.Rounded.Home,
                            title = stringResource(R.string.process_host),
                            description = state.mainDetail,
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.Rounded.Build,
                            title = stringResource(R.string.process_probe),
                            description = state.phantomDetail,
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.Rounded.Share,
                            title = stringResource(R.string.process_handshake),
                            description = state.handshakeDetail,
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_pipeline)) {
                    item {
                        BaseWidget(
                            icon = Icons.Rounded.Add,
                            title = stringResource(R.string.stage_fork),
                            description = state.forkDetail,
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.AutoMirrored.Rounded.Send,
                            title = stringResource(R.string.stage_poke),
                            description = state.pokeDetail,
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.Rounded.DateRange,
                            title = stringResource(R.string.stage_settle),
                            description = state.settleDetail,
                        )
                    }
                    item {
                        BaseWidget(
                            icon = Icons.Rounded.Search,
                            title = stringResource(R.string.stage_observe),
                            description = state.observeDetail,
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_how)) {
                    item {
                        NavigationItemWidget(
                            icon = Icons.AutoMirrored.Rounded.List,
                            title = stringResource(R.string.row_protocol),
                            description = stringResource(R.string.row_protocol_desc),
                            onClick = { sheet = Sheet.Protocol },
                        )
                    }
                    item {
                        NavigationItemWidget(
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.row_isolation),
                            description = stringResource(R.string.row_isolation_desc),
                            onClick = { sheet = Sheet.Isolation },
                        )
                    }
                }
            }

            item {
                SegmentedColumn(title = stringResource(R.string.section_system)) {
                    item {
                        NavigationItemWidget(
                            icon = Icons.Rounded.Settings,
                            title = stringResource(R.string.row_developer_options),
                            description = stringResource(R.string.row_developer_options_desc),
                            onClick = onOpenDeveloperOptions,
                        )
                    }
                }
            }
        }
    }

    val open = sheet
    if (open != null) {
        // Content clears the system navigation bar itself: zero insets on the sheet plus a
        // matching spacer at the end of the list, so the last line is never covered.
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentWindowInsets = {
                WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
            },
        ) {
            SheetContent(open)
        }
    }
}

/** The two explanations. They share one layout; only the resource ids differ. */
private enum class Sheet(@StringRes val title: Int, @StringRes val lead: Int, val points: List<Int>) {
    Protocol(
        title = R.string.sheet_protocol_title,
        lead = R.string.sheet_protocol_lead,
        points = listOf(
            R.string.sheet_protocol_point_1,
            R.string.sheet_protocol_point_2,
            R.string.sheet_protocol_point_3,
            R.string.sheet_protocol_point_4,
            R.string.sheet_protocol_point_5,
        ),
    ),
    Isolation(
        title = R.string.sheet_isolation_title,
        lead = R.string.sheet_isolation_lead,
        points = listOf(
            R.string.sheet_isolation_point_1,
            R.string.sheet_isolation_point_2,
            R.string.sheet_isolation_point_3,
            R.string.sheet_isolation_point_4,
        ),
    ),
}

@Composable
private fun SheetContent(sheet: Sheet) {
    val title = stringResource(sheet.title)
    val lead = stringResource(sheet.lead)
    val points = sheet.points.map { stringResource(it) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            Text(
                text = lead,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(points) { index, point ->
            Text(
                text = "${index + 1}. $point",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
