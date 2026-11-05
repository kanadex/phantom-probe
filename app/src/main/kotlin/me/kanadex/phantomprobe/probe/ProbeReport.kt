// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

import android.os.Bundle

/** Where a run currently is. Only [FORKING] and [SETTLING] are short enough to say "detecting". */
enum class ProbePhase {
    IDLE,
    FORKING,
    SETTLING,
    OBSERVING,
    DONE,
    ;

    /** True while the probe still owns children on the device. */
    val running: Boolean get() = this == FORKING || this == SETTLING || this == OBSERVING
}

/** What the run concluded. [RELAXED] is an observation, not a proof: no child was reaped. */
enum class ProbeVerdict {
    UNKNOWN,
    NOT_NEEDED,
    RELAXED,
    LIMITED,
    ;

    /** Worth remembering across launches. */
    val conclusive: Boolean get() = this != UNKNOWN

    /** The healthy side of the answer, used to pick the status card colour. */
    val ok: Boolean get() = this == RELAXED || this == NOT_NEEDED
}

/** Why a run stopped without a fresh verdict. */
enum class ProbeFailure {
    NONE,

    /** The user stopped the run. */
    CANCELLED,

    /** fork() failed; the process hit its own task limit or lacks address space. */
    FORK_FAILED,

    /** The previous run did not release its children in time. */
    REJECTED,

    /** Nothing answered: the probe process was refused, killed or never started. */
    LOST,

    /** Below API 31 there is no cap to enforce. */
    UNSUPPORTED,
}

/**
 * A snapshot of one run. Every field is absolute, so a consumer never has to reconstruct state
 * from a stream of deltas, and a lost report cannot corrupt what the UI shows.
 */
data class ProbeReport(
    val phase: ProbePhase = ProbePhase.IDLE,
    val verdict: ProbeVerdict = ProbeVerdict.UNKNOWN,
    val failure: ProbeFailure = ProbeFailure.NONE,
    val forked: Int = 0,
    val reapedPid: Int = 0,
    val reapedPhase: ProbePhase = ProbePhase.IDLE,
    val hostPid: Int = 0,
    val handshakePid: Int = 0,
    val poked: Boolean = false,
    val forkMillis: Long = 0,
    val settleMillis: Long = 0,
    val elapsedMillis: Long = 0,
) {

    fun toBundle(): Bundle = Bundle().apply {
        putString(ProbeProtocol.KEY_PHASE, phase.name)
        putString(ProbeProtocol.KEY_VERDICT, verdict.name)
        putString(ProbeProtocol.KEY_FAILURE, failure.name)
        putInt(ProbeProtocol.KEY_FORKED, forked)
        putInt(ProbeProtocol.KEY_REAPED_PID, reapedPid)
        putString(ProbeProtocol.KEY_REAPED_PHASE, reapedPhase.name)
        putInt(ProbeProtocol.KEY_HOST_PID, hostPid)
        putInt(ProbeProtocol.KEY_HANDSHAKE_PID, handshakePid)
        putBoolean(ProbeProtocol.KEY_POKED, poked)
        putLong(ProbeProtocol.KEY_FORK_MILLIS, forkMillis)
        putLong(ProbeProtocol.KEY_SETTLE_MILLIS, settleMillis)
        putLong(ProbeProtocol.KEY_ELAPSED_MILLIS, elapsedMillis)
    }

    companion object {

        fun fromBundle(bundle: Bundle): ProbeReport = ProbeReport(
            phase = bundle.enum(ProbeProtocol.KEY_PHASE, ProbePhase.DONE),
            verdict = bundle.enum(ProbeProtocol.KEY_VERDICT, ProbeVerdict.UNKNOWN),
            failure = bundle.enum(ProbeProtocol.KEY_FAILURE, ProbeFailure.NONE),
            forked = bundle.getInt(ProbeProtocol.KEY_FORKED),
            reapedPid = bundle.getInt(ProbeProtocol.KEY_REAPED_PID),
            reapedPhase = bundle.enum(ProbeProtocol.KEY_REAPED_PHASE, ProbePhase.IDLE),
            hostPid = bundle.getInt(ProbeProtocol.KEY_HOST_PID),
            handshakePid = bundle.getInt(ProbeProtocol.KEY_HANDSHAKE_PID),
            poked = bundle.getBoolean(ProbeProtocol.KEY_POKED),
            forkMillis = bundle.getLong(ProbeProtocol.KEY_FORK_MILLIS),
            settleMillis = bundle.getLong(ProbeProtocol.KEY_SETTLE_MILLIS),
            elapsedMillis = bundle.getLong(ProbeProtocol.KEY_ELAPSED_MILLIS),
        )

        private inline fun <reified T : Enum<T>> Bundle.enum(key: String, fallback: T): T {
            val name = getString(key) ?: return fallback
            return enumValues<T>().firstOrNull { it.name == name } ?: fallback
        }
    }
}

/** A verdict that survived a process restart. Written only for [ProbeVerdict.conclusive]. */
data class ProbeRecord(
    val verdict: ProbeVerdict,
    val forked: Int,
    val forkMillis: Long,
    val settleMillis: Long,
    val elapsedMillis: Long,
    val at: Long,
)
