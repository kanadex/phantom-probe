// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

/**
 * Timings and wire keys shared by the host process, [:phantom] and [:probe].
 *
 * No user-visible text lives here. A report carries numbers and enums only, so the three
 * processes stay language-agnostic and every string is resolved in the UI layer.
 */
internal object ProbeProtocol {

    /** AOSP default for phantom processes per app. Used for copy only, never for the verdict. */
    const val SYSTEM_CAP = 32

    /**
     * Deliberately above any cap a ROM is known to ship (20 / 32 / 64). The verdict does not
     * depend on the exact number: it depends on whether the system reaps any child at all.
     */
    const val FORK_COUNT = 66

    /** How long the system gets to trim after the app process set changed. */
    const val SETTLE_MILLIS = 3_000L

    /** How long the probe keeps watching after publishing the early verdict. */
    const val OBSERVE_MILLIS = 60_000L

    /** Child poll interval. 200 ms is well below the settle window and costs nothing. */
    const val POLL_MILLIS = 200L

    /** Upper bound on report frequency; the UI does not need every single fork. */
    const val REPORT_INTERVAL_MILLIS = 400L

    /** How long a new run waits for the previous one to release its children. */
    const val STOP_TIMEOUT_MILLIS = 2_000L

    const val ACTION_START = "me.kanadex.phantomprobe.action.START"
    const val ACTION_CANCEL = "me.kanadex.phantomprobe.action.CANCEL"
    const val EXTRA_CALLBACK = "me.kanadex.phantomprobe.extra.CALLBACK"
    const val RESULT_REPORT = 1

    const val KEY_PHASE = "phase"
    const val KEY_VERDICT = "verdict"
    const val KEY_FAILURE = "failure"
    const val KEY_FORKED = "forked"
    const val KEY_REAPED_PID = "reapedPid"
    const val KEY_REAPED_PHASE = "reapedPhase"
    const val KEY_HOST_PID = "hostPid"
    const val KEY_HANDSHAKE_PID = "handshakePid"
    const val KEY_POKED = "poked"
    const val KEY_FORK_MILLIS = "forkMillis"
    const val KEY_SETTLE_MILLIS = "settleMillis"
    const val KEY_ELAPSED_MILLIS = "elapsedMillis"

    /** Created by the probe, deleted by the handshake process: the deletion is the receipt. */
    const val MARKER_FILE = "receivedBroad"

    /** Written by the handshake process so the probe can show which process answered. */
    const val HANDSHAKE_FILE = "probe.pid"
}
