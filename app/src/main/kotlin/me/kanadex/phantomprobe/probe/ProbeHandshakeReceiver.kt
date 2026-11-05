// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
package me.kanadex.phantomprobe.probe

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Runs in `:probe`, the second app process the probe brings up on purpose.
 *
 * AMS re-counts phantom processes when the app process set changes, so this broadcast is the
 * trigger, not a courtesy call. The receiver leaves two marks and exits:
 *
 * 1. delete the marker file the probe created — the deletion is the receipt the probe polls;
 * 2. write its own pid, so the report can name the process that answered.
 *
 * It then asks the system for a background ANR and dies. A process with no visible UI is
 * killed silently, which is exactly what we want: no dialog, no residue, and a death event
 * that makes AMS look at the phantom process list once more. Both calls are best effort —
 * the two marks are already on disk, so a ROM that refuses the ANR changes nothing.
 */
class ProbeHandshakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pid = Process.myPid()

        runCatching { File(app.dataDir, ProbeProtocol.MARKER_FILE).delete() }
        runCatching { File(app.dataDir, ProbeProtocol.HANDSHAKE_FILE).writeText(pid.toString()) }
        Log.i(TAG, "handshake from pid $pid")

        runCatching {
            app.getSystemService(ActivityManager::class.java)?.appNotResponding("PhantomProbe handshake")
        }.onFailure { Log.w(TAG, "appNotResponding refused: ${it.message}") }

        Process.killProcess(pid)
        PhantomProcessNative.killPid(pid)
    }

    private companion object {
        const val TAG = "PhantomProbeProbe"
    }
}
