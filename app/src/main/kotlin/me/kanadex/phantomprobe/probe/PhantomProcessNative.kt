// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

/**
 * The only native surface of the probe (libphantom_probe.so).
 *
 * A child created here never returns to the JVM: it stays in a sleep loop until something
 * reaps it. That is what makes "the child is gone" meaningful — no child can exit by itself.
 */
internal object PhantomProcessNative {

    init {
        System.loadLibrary("phantom_probe")
    }

    /** Forks a child that only sleeps. Returns its pid, or -1 on failure. */
    @JvmStatic
    external fun forkNullLoop(): Int

    /** Returns the reaped pid, 0 while it still runs, -1 when it is no longer our child. */
    @JvmStatic
    external fun waitPidNoHang(pid: Int): Int

    /** Sends SIGKILL. Invalid pids are ignored. */
    @JvmStatic
    external fun killPid(pid: Int)

    /** Waits up to [timeoutMillis] for [pid]: returns the pid, 0 on timeout, -1 when not ours. */
    @JvmStatic
    external fun reapPid(pid: Int, timeoutMillis: Int): Int
}
