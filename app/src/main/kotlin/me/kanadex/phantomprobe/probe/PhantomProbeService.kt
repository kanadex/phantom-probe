// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.ResultReceiver
import android.util.Log
import androidx.core.content.IntentCompat
import java.util.concurrent.Executors

/**
 * The probe process (`android:process=":phantom"`): fork, wait and reap happen only here.
 *
 * fork() copies the page tables of the calling address space while holding that space'
 * mmap_lock for the write side. The UI thread needs the same lock for every allocation it
 * makes, so 66 forks in the host process would stall Compose for roughly 300 ms per tap.
 * mmap_lock belongs to the address space, not to a thread, so another thread of the same
 * process buys nothing; another process is the only real fix.
 *
 * Control operations are serialised on [control] because they block: a run has to release
 * its children before the next one starts. Reports go back through the caller's
 * [ResultReceiver]; the host process performs one startService and nothing else.
 */
class PhantomProbeService : Service() {

    private class Active(val engine: ProbeEngine, val thread: Thread)

    private val control = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "probe-control").apply { isDaemon = true }
    }

    @Volatile
    private var active: Active? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ProbeProtocol.ACTION_CANCEL -> control.execute { stopActive() }

            ProbeProtocol.ACTION_START -> {
                val callback = IntentCompat.getParcelableExtra(
                    intent,
                    ProbeProtocol.EXTRA_CALLBACK,
                    ResultReceiver::class.java,
                )
                control.execute {
                    if (stopActive()) {
                        startActive(callback, startId)
                    } else {
                        report(callback, ProbeReport(phase = ProbePhase.DONE, failure = ProbeFailure.REJECTED))
                    }
                }
            }

            else -> return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        control.execute { stopActive() }
        control.shutdown()
        super.onDestroy()
    }

    /** Returns true when no run is left behind. */
    private fun stopActive(): Boolean {
        val run = active ?: return true
        run.engine.cancel()
        run.thread.join(ProbeProtocol.STOP_TIMEOUT_MILLIS)
        if (run.thread.isAlive) {
            Log.w(TAG, "previous run did not release its children in time")
            return false
        }
        active = null
        return true
    }

    private fun startActive(callback: ResultReceiver?, startId: Int) {
        val engine = ProbeEngine(applicationContext) { report -> report(callback, report) }
        val thread = Thread(
            {
                try {
                    engine.run()
                } catch (failure: Throwable) {
                    Log.e(TAG, "probe run failed", failure)
                } finally {
                    if (active?.engine === engine) active = null
                    stopSelfResult(startId)
                }
            },
            "probe-engine",
        )
        active = Active(engine, thread)
        Log.i(TAG, "run started in pid ${Process.myPid()}")
        thread.start()
    }

    private fun report(callback: ResultReceiver?, report: ProbeReport) {
        runCatching { callback?.send(ProbeProtocol.RESULT_REPORT, report.toBundle()) }
            .onFailure { Log.w(TAG, "report dropped: ${it.message}") }
    }

    companion object {

        private const val TAG = "PhantomProbeService"

        /**
         * Fire-and-forget. Called from the host process' single background queue, never from a
         * click callback: startService is a synchronous Binder call that may have to cold-start
         * this process.
         */
        fun start(context: Context, callback: ResultReceiver) {
            val intent = Intent(context, PhantomProbeService::class.java).apply {
                action = ProbeProtocol.ACTION_START
                putExtra(ProbeProtocol.EXTRA_CALLBACK, callback)
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "start refused: ${it.message}") }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, PhantomProbeService::class.java).apply {
                action = ProbeProtocol.ACTION_CANCEL
            }
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "cancel refused: ${it.message}") }
        }
    }
}
