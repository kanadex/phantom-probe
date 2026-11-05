// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The host process' view of the probe. One instance per process, one queue, no fork.
 *
 * Three rules keep the UI thread out of the way:
 *
 * - `startService` and the cancel intent are Binder calls, so they are only ever issued from
 *   [queue] (a single background thread), never from a click callback;
 * - disk reads and writes go through the same queue;
 * - reports arrive on the main looper but are already plain data: applying one is a single
 *   state write, not work.
 */
object ProbeClient {

    private const val TAG = "PhantomProbe"

    data class Status(
        val report: ProbeReport = ProbeReport(),
        /** Last conclusive verdict from an earlier launch, shown until this run reports. */
        val record: ProbeRecord? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private val queue: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "probe-client").apply { isDaemon = true }
        }

    /** A start that never produced a report: the probe process was refused or killed. */
    private const val START_GUARD_MILLIS = 8_000L

    /** A run that never finished: settle plus observation, with slack for a slow device. */
    private const val RUN_GUARD_MILLIS = ProbeProtocol.SETTLE_MILLIS + ProbeProtocol.OBSERVE_MILLIS + 10_000L

    @Volatile
    private var app: Context? = null

    /** Identifies the current run so a guard from an earlier run cannot end a newer one. */
    @Volatile
    private var runId = 0

    /** Binds the application context and restores the last verdict. Safe to call repeatedly. */
    fun attach(context: Context) {
        if (app != null) return
        val application = context.applicationContext
        app = application
        queue.execute {
            val record = ProbeRecordStore.load(application) ?: return@execute
            // A report that already arrived is newer than anything on disk.
            _status.update { status ->
                if (status.report.verdict.conclusive) status else status.copy(record = record)
            }
        }
    }

    /**
     * Launch-time probe. Only the first caller of this process gets a run; a rotation or a
     * recomposition sees a report that is no longer [ProbePhase.IDLE] and does nothing.
     */
    fun probeOnLaunch() {
        val report = _status.value.report
        if (report.phase != ProbePhase.IDLE || report.verdict.conclusive) return
        start()
    }

    fun start() {
        val context = app ?: return
        if (_status.value.report.phase.running) return
        // Optimistic phase so the card reacts on the same frame as the tap.
        _status.update { it.copy(report = ProbeReport(phase = ProbePhase.FORKING)) }
        val callback = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (resultCode != ProbeProtocol.RESULT_REPORT || resultData == null) return
                accept(ProbeReport.fromBundle(resultData))
            }
        }
        val id = ++runId
        queue.execute { PhantomProbeService.start(context, callback) }
        queue.schedule({ guardStart(id) }, START_GUARD_MILLIS, TimeUnit.MILLISECONDS)
        queue.schedule({ guardRun(id) }, RUN_GUARD_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun cancel() {
        val context = app ?: return
        val report = _status.value.report
        if (!report.phase.running) return
        // The engine also reports DONE once its children are gone; this covers the case where
        // the service process dies before it can answer.
        _status.update {
            it.copy(report = it.report.copy(phase = ProbePhase.DONE, failure = ProbeFailure.CANCELLED))
        }
        queue.execute { PhantomProbeService.cancel(context) }
    }

    /**
     * A start that never produced a report means nobody is running: the process was refused,
     * killed on the way up, or the intent never left. Fail loudly instead of spinning.
     */
    private fun guardStart(id: Int) {
        if (id != runId) return
        _status.update { status ->
            val report = status.report
            if (report.phase == ProbePhase.FORKING && report.hostPid == 0) {
                status.copy(report = report.copy(phase = ProbePhase.DONE, failure = ProbeFailure.LOST))
            } else {
                status
            }
        }
    }

    /** A run outlives its budget when the probe process died without a final report. */
    private fun guardRun(id: Int) {
        if (id != runId) return
        _status.update { status ->
            if (status.report.phase.running) {
                status.copy(report = status.report.copy(phase = ProbePhase.DONE, failure = ProbeFailure.LOST))
            } else {
                status
            }
        }
    }

    private fun accept(report: ProbeReport) {
        _status.update { it.copy(report = report) }
        if (!report.verdict.conclusive) return

        val record = ProbeRecord(
            verdict = report.verdict,
            forked = report.forked,
            forkMillis = report.forkMillis,
            settleMillis = report.settleMillis,
            elapsedMillis = report.elapsedMillis,
            at = System.currentTimeMillis(),
        )
        _status.update { it.copy(record = record) }
        val context = app ?: return
        queue.execute { ProbeRecordStore.save(context, record) }
    }
}

/** Persists exactly one record. Only conclusive verdicts are written. */
internal object ProbeRecordStore {

    private const val FILE = "probe_record"
    private const val KEY_VERDICT = "verdict"
    private const val KEY_FORKED = "forked"
    private const val KEY_FORK_MILLIS = "forkMillis"
    private const val KEY_SETTLE_MILLIS = "settleMillis"
    private const val KEY_ELAPSED_MILLIS = "elapsedMillis"
    private const val KEY_AT = "at"

    fun load(context: Context): ProbeRecord? {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val name = preferences.getString(KEY_VERDICT, null) ?: return null
        val verdict = ProbeVerdict.entries.firstOrNull { it.name == name } ?: return null
        if (!verdict.conclusive) return null
        return ProbeRecord(
            verdict = verdict,
            forked = preferences.getInt(KEY_FORKED, 0),
            forkMillis = preferences.getLong(KEY_FORK_MILLIS, 0),
            settleMillis = preferences.getLong(KEY_SETTLE_MILLIS, 0),
            elapsedMillis = preferences.getLong(KEY_ELAPSED_MILLIS, 0),
            at = preferences.getLong(KEY_AT, 0),
        )
    }

    fun save(context: Context, record: ProbeRecord) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VERDICT, record.verdict.name)
            .putInt(KEY_FORKED, record.forked)
            .putLong(KEY_FORK_MILLIS, record.forkMillis)
            .putLong(KEY_SETTLE_MILLIS, record.settleMillis)
            .putLong(KEY_ELAPSED_MILLIS, record.elapsedMillis)
            .putLong(KEY_AT, record.at)
            .apply()
    }
}
