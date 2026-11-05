// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * The detection state machine. Instantiated in the `:phantom` process and nowhere else.
 *
 * The protocol is five steps and each one exists for a reason:
 *
 * 1. fork [ProbeProtocol.FORK_COUNT] children that only sleep, with no exec and no return to
 *    Java, so nothing but the system can end them;
 * 2. send an explicit broadcast to a second app process. AMS re-counts phantom processes when
 *    the app process set changes, so this poke is what makes the system look;
 * 3. poll `waitpid(pid, WNOHANG)` per child. A child that reports back without us killing it
 *    was reaped by the system — that is the only evidence worth reporting;
 * 4. publish early: if nothing is reaped inside the settle window, say so immediately and keep
 *    watching in the background, upgrading the verdict if a child dies later;
 * 5. kill and reap the survivors, then let the process exit. Children carry PR_SET_PDEATHSIG,
 *    so nothing is left behind if the process dies first.
 *
 * The engine reports snapshots and never text; [emit] is called from [run]'s thread.
 */
internal class ProbeEngine(
    private val app: Context,
    private val emit: (ProbeReport) -> Unit,
) {

    private val children = ArrayList<Int>(ProbeProtocol.FORK_COUNT)
    private val startedAt = SystemClock.elapsedRealtime()
    private var lastEmitAt = 0L

    @Volatile
    private var cancelled = false

    private companion object {
        const val TAG = "PhantomProbe"
    }

    /** Asks [run] to stop. The run releases its children and reports a final snapshot. */
    fun cancel() {
        cancelled = true
    }

    fun run() {
        var report = ProbeReport(phase = ProbePhase.FORKING, hostPid = Process.myPid())
        publish(report)
        Log.i(TAG, "run start: pid ${report.hostPid}, api ${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            finish(report.copy(phase = ProbePhase.DONE, verdict = ProbeVerdict.NOT_NEEDED, failure = ProbeFailure.UNSUPPORTED))
            return
        }

        // A marker or pid file left by an earlier run would be indistinguishable from this one.
        marker().delete()
        handshake().delete()

        // Step 1: fork.
        var failure = ProbeFailure.NONE
        for (index in 0 until ProbeProtocol.FORK_COUNT) {
            if (cancelled) {
                failure = ProbeFailure.CANCELLED
                break
            }
            val pid = PhantomProcessNative.forkNullLoop()
            if (pid <= 0) {
                failure = ProbeFailure.FORK_FAILED
                break
            }
            children.add(pid)
            if (children.size % 8 == 0) publishIfDue(report.copy(forked = children.size))
        }

        val forkMillis = elapsed()
        report = report.copy(forked = children.size, forkMillis = forkMillis)
        if (failure != ProbeFailure.NONE) {
            Log.w(TAG, "fork stopped after ${children.size} children: $failure")
            finish(report.copy(phase = ProbePhase.DONE, failure = failure))
            return
        }
        Log.i(TAG, "forked ${children.size} children in ${forkMillis}ms")
        publish(report)

        // Step 2: poke the second app process.
        report = report.copy(poked = poke())
        Log.i(TAG, "poke sent: ${report.poked}")
        publish(report)

        // Step 3: settle. Any reaped child here is the strongest evidence there is.
        val settleStart = elapsed()
        publish(report.copy(phase = ProbePhase.SETTLING))
        while (!cancelled && elapsed() - settleStart < ProbeProtocol.SETTLE_MILLIS) {
            val reaped = reapOne()
            if (reaped > 0) {
                Log.i(TAG, "pid $reaped reaped by the system after ${elapsed()}ms (settle)")
                finish(
                    report.copy(
                        phase = ProbePhase.DONE,
                        verdict = ProbeVerdict.LIMITED,
                        reapedPid = reaped,
                        reapedPhase = ProbePhase.SETTLING,
                        settleMillis = elapsed() - settleStart,
                    ),
                )
                return
            }
            if (report.handshakePid == 0) {
                val handshakePid = readHandshakePid()
                if (handshakePid > 0) {
                    report = report.copy(handshakePid = handshakePid)
                    publish(report.copy(phase = ProbePhase.SETTLING, settleMillis = elapsed() - settleStart))
                }
            }
            sleep(ProbeProtocol.POLL_MILLIS)
            publishIfDue(report.copy(phase = ProbePhase.SETTLING, settleMillis = elapsed() - settleStart))
        }
        if (cancelled) {
            finish(
                report.copy(
                    phase = ProbePhase.DONE,
                    failure = ProbeFailure.CANCELLED,
                    settleMillis = elapsed() - settleStart,
                ),
            )
            return
        }

        // Step 4: publish the early verdict, then keep watching.
        report = report.copy(verdict = ProbeVerdict.RELAXED, settleMillis = elapsed() - settleStart)
        publish(report.copy(phase = ProbePhase.OBSERVING))

        val observeStart = elapsed()
        while (!cancelled && elapsed() - observeStart < ProbeProtocol.OBSERVE_MILLIS) {
            val reaped = reapOne()
            if (reaped > 0) {
                Log.i(TAG, "pid $reaped reaped by the system after ${elapsed()}ms (observation)")
                finish(
                    report.copy(
                        phase = ProbePhase.DONE,
                        verdict = ProbeVerdict.LIMITED,
                        reapedPid = reaped,
                        reapedPhase = ProbePhase.OBSERVING,
                    ),
                )
                return
            }
            sleep(ProbeProtocol.POLL_MILLIS)
            publishIfDue(report.copy(phase = ProbePhase.OBSERVING))
        }
        finish(
            report.copy(
                phase = ProbePhase.DONE,
                failure = if (cancelled) ProbeFailure.CANCELLED else ProbeFailure.NONE,
            ),
        )
    }

    /** Step 5: release everything, then report the final snapshot. */
    private fun finish(report: ProbeReport) {
        cleanup()
        val final = report.copy(phase = ProbePhase.DONE)
        Log.i(
            TAG,
            "run done: verdict=${final.verdict} failure=${final.failure} elapsed=${elapsed()}ms",
        )
        publish(final)
    }

    private fun cleanup() {
        val snapshot = children.toList()
        children.clear()
        snapshot.forEach { PhantomProcessNative.killPid(it) }
        snapshot.forEach { PhantomProcessNative.reapPid(it, 200) }
        marker().delete()
        handshake().delete()
    }

    /**
     * Reaps at most one finished child. Returns the pid when the child is gone, 0 otherwise.
     *
     * A child that disappears *without* being reaped here (ECHILD) is dropped from the list but
     * is not evidence of a system trim, so it never becomes a [ProbeVerdict.LIMITED].
     */
    private fun reapOne(): Int {
        if (children.isEmpty()) return 0
        val snapshot = children.toList()
        for (pid in snapshot) {
            val state = PhantomProcessNative.waitPidNoHang(pid)
            if (state == 0) continue
            children.remove(pid)
            if (state > 0) return pid
        }
        return 0
    }

    private fun poke(): Boolean {
        // The marker is the receipt the handshake receiver deletes.
        val created = runCatching { marker().createNewFile() }.getOrDefault(false)
        if (!created && !marker().exists()) return false
        return runCatching { app.sendBroadcast(Intent(app, ProbeHandshakeReceiver::class.java)) }
            .isSuccess
    }

    private fun readHandshakePid(): Int {
        val file = handshake()
        if (!file.exists()) return 0
        return runCatching { file.readText().trim().toIntOrNull() ?: 0 }.getOrDefault(0)
    }

    private fun marker(): File = File(app.dataDir, ProbeProtocol.MARKER_FILE)

    private fun handshake(): File = File(app.dataDir, ProbeProtocol.HANDSHAKE_FILE)

    private fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt

    private fun publish(report: ProbeReport) {
        lastEmitAt = elapsed()
        emit(report.copy(elapsedMillis = lastEmitAt))
    }

    private fun publishIfDue(report: ProbeReport) {
        if (elapsed() - lastEmitAt < ProbeProtocol.REPORT_INTERVAL_MILLIS) return
        publish(report)
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (interrupted: InterruptedException) {
            cancelled = true
        }
    }
}
