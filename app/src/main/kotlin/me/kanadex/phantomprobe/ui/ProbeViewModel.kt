// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import me.kanadex.phantomprobe.R
import me.kanadex.phantomprobe.probe.ProbeClient
import me.kanadex.phantomprobe.probe.ProbeFailure
import me.kanadex.phantomprobe.probe.ProbePhase
import me.kanadex.phantomprobe.probe.ProbeProtocol
import me.kanadex.phantomprobe.probe.ProbeRecord
import me.kanadex.phantomprobe.probe.ProbeReport
import me.kanadex.phantomprobe.probe.ProbeVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The only place where a report becomes text.
 *
 * Everything the card and the rows need is computed here: the verdict, the phase arithmetic,
 * the durations, the restored-record wording. The screen receives strings, so a second screen
 * would make exactly the same calls instead of re-deriving the same sentences.
 *
 * `init` only subscribes and hands the context to [ProbeClient]; nothing here reads disk, and
 * no action blocks: starting or cancelling a probe posts one intent to the client queue.
 */
class ProbeViewModel(
    private val app: Application,
    private val scope: CoroutineScope,
) {

    private val hostPid = Process.myPid()

    private val _state = MutableStateFlow(render(ProbeClient.status.value))
    val state: StateFlow<ProbeUiState> = _state.asStateFlow()

    init {
        ProbeClient.attach(app)
        scope.launch {
            ProbeClient.status.collect { status -> _state.value = render(status) }
        }
    }

    fun dispatch(action: ProbeAction) {
        when (action) {
            ProbeAction.Toggle -> if (ProbeClient.status.value.report.phase.running) {
                ProbeClient.cancel()
            } else {
                ProbeClient.start()
            }

            ProbeAction.Launch -> ProbeClient.probeOnLaunch()

            // startActivity is a synchronous Binder call that may cold-start the settings
            // process; it belongs on a background dispatcher, not in the tap frame.
            ProbeAction.OpenDeveloperOptions -> scope.launch(Dispatchers.IO) {
                runCatching {
                    app.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { Log.w(TAG, "developer options unavailable: ${it.message}") }
            }
        }
    }

    private fun render(status: ProbeClient.Status): ProbeUiState {
        val report = status.report
        val record = status.record
        val verdict = if (report.verdict.conclusive) report.verdict else record?.verdict ?: ProbeVerdict.UNKNOWN
        // Only these two phases are short enough to be announced as "probing"; the observation
        // window keeps showing the verdict it already published.
        val pending = report.phase == ProbePhase.FORKING || report.phase == ProbePhase.SETTLING

        return ProbeUiState(
            title = when {
                pending -> text(R.string.status_probing)
                verdict == ProbeVerdict.NOT_NEEDED -> text(R.string.status_not_needed)
                verdict == ProbeVerdict.RELAXED -> text(R.string.status_relaxed)
                verdict == ProbeVerdict.LIMITED -> text(R.string.status_limited)
                else -> text(R.string.status_unknown)
            },
            detail = detail(report, record),
            actionText = if (report.phase.running) text(R.string.action_stop) else text(R.string.action_run),
            ok = verdict.ok,
            mainDetail = text(R.string.process_host_detail, hostPid),
            phantomDetail = if (report.hostPid > 0) {
                text(R.string.process_probe_running, report.hostPid, report.forked)
            } else {
                text(R.string.process_probe_idle)
            },
            handshakeDetail = when {
                report.handshakePid > 0 -> text(R.string.process_handshake_seen, report.handshakePid)
                report.poked -> text(R.string.process_handshake_missing)
                else -> text(R.string.process_handshake_idle)
            },
            forkDetail = when {
                report.forked == 0 -> text(R.string.stage_fork_idle)
                report.forkMillis == 0L -> text(
                    R.string.stage_fork_running,
                    report.forked,
                    ProbeProtocol.FORK_COUNT,
                )

                else -> text(R.string.stage_fork_done, report.forked, seconds(report.forkMillis))
            },
            pokeDetail = when {
                report.handshakePid > 0 -> text(R.string.stage_poke_acked, report.handshakePid)
                report.poked -> text(R.string.stage_poke_sent)
                else -> text(R.string.stage_poke_idle)
            },
            settleDetail = when {
                report.settleMillis == 0L -> text(R.string.stage_settle_idle)
                report.reapedPhase == ProbePhase.SETTLING -> text(R.string.stage_settle_reaped, report.reapedPid)
                else -> text(R.string.stage_settle_done, seconds(report.settleMillis))
            },
            observeDetail = when {
                report.settleMillis == 0L -> text(R.string.stage_observe_idle)
                report.reapedPhase == ProbePhase.SETTLING -> text(R.string.stage_observe_skipped)
                report.reapedPhase == ProbePhase.OBSERVING -> text(R.string.stage_observe_reaped, report.reapedPid)
                report.phase == ProbePhase.OBSERVING -> text(
                    R.string.stage_observe_running,
                    seconds(observedMillis(report)),
                    seconds(ProbeProtocol.OBSERVE_MILLIS),
                )

                else -> text(R.string.stage_observe_done, seconds(observedMillis(report)))
            },
        )
    }

    private fun detail(report: ProbeReport, record: ProbeRecord?): String {
        when (report.phase) {
            ProbePhase.FORKING -> return text(
                R.string.detail_forking,
                report.forked,
                ProbeProtocol.FORK_COUNT,
            )

            ProbePhase.SETTLING -> return text(
                R.string.detail_settling,
                report.forked,
                seconds(ProbeProtocol.SETTLE_MILLIS),
            )

            ProbePhase.OBSERVING -> return text(
                R.string.detail_observing,
                report.forked,
                seconds(report.settleMillis),
                seconds(observedMillis(report)),
                seconds(ProbeProtocol.OBSERVE_MILLIS),
            )

            ProbePhase.IDLE, ProbePhase.DONE -> Unit
        }

        when (report.failure) {
            ProbeFailure.FORK_FAILED -> return text(R.string.detail_fork_failed, report.forked)
            // A run stopped during observation already published a verdict; only an early stop
            // has nothing to report.
            ProbeFailure.CANCELLED -> if (!report.verdict.conclusive) {
                return text(R.string.detail_cancelled, report.forked)
            }
            ProbeFailure.REJECTED -> return text(R.string.detail_rejected)
            ProbeFailure.LOST -> return text(R.string.detail_lost)
            ProbeFailure.NONE, ProbeFailure.UNSUPPORTED -> Unit
        }

        if (report.verdict.conclusive) {
            return when (report.verdict) {
                ProbeVerdict.NOT_NEEDED -> text(R.string.detail_not_needed, Build.VERSION.SDK_INT)
                ProbeVerdict.LIMITED -> text(
                    R.string.detail_limited,
                    report.reapedPid,
                    seconds(report.elapsedMillis),
                    report.forked,
                )

                ProbeVerdict.RELAXED -> text(
                    R.string.detail_relaxed,
                    report.forked,
                    seconds(report.settleMillis),
                    seconds(observedMillis(report)),
                )

                ProbeVerdict.UNKNOWN -> ""
            }
        }

        if (record != null) {
            val summary = when (record.verdict) {
                ProbeVerdict.NOT_NEEDED -> text(R.string.summary_not_needed, Build.VERSION.SDK_INT)
                ProbeVerdict.LIMITED -> text(R.string.summary_limited, record.forked)
                ProbeVerdict.RELAXED -> text(
                    R.string.summary_relaxed,
                    record.forked,
                    seconds(record.settleMillis),
                )

                ProbeVerdict.UNKNOWN -> ""
            }
            return summary + "\n" + text(R.string.detail_recorded, relativeTime(record.at))
        }

        return text(R.string.detail_unknown, ProbeProtocol.FORK_COUNT)
    }

    private fun observedMillis(report: ProbeReport): Long =
        (report.elapsedMillis - report.forkMillis - report.settleMillis).coerceAtLeast(0)

    private fun seconds(millis: Long): String = text(R.string.duration_seconds, millis / 1000.0)

    private fun relativeTime(at: Long): CharSequence = DateUtils.getRelativeTimeSpanString(
        at,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )

    private fun text(@StringRes id: Int, vararg arguments: Any): String =
        app.getString(id, *arguments)

    private companion object {
        const val TAG = "PhantomProbe"
    }
}

@Composable
fun rememberProbeViewModel(): ProbeViewModel {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as Application }
    val scope = rememberCoroutineScope()
    return remember(app, scope) { ProbeViewModel(app, scope) }
}
