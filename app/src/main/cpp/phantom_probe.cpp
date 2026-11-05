// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 kanadex
//
// Android 12 (API 31) caps how many unsandboxed child processes an app may hold. The cap has
// no public API: it is enforced by ActivityManager when it trims phantom processes, so the
// only honest way to observe it is to create children and watch whether they survive.
//
// These four primitives are the whole native surface; every decision lives in Kotlin
// (probe/ProbeEngine.kt). A child never returns to Java and never exec()s:
//   1. it stays a phantom process, so no exec sandbox applies to it;
//   2. it cannot exit on its own, so a reaped child is evidence, not a normal exit;
//   3. PR_SET_PDEATHSIG ties its lifetime to the probe process, so no orphan survives.

#include <jni.h>
#include <errno.h>
#include <signal.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <unistd.h>

extern "C" {

/*
 * Forks a child that only sleeps. Returns the child pid in the parent, -1 on failure.
 * The child never returns.
 */
JNIEXPORT jint JNICALL
Java_me_kanadex_phantomprobe_probe_PhantomProcessNative_forkNullLoop(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    pid_t pid = fork();
    if (pid == 0) {
        prctl(PR_SET_PDEATHSIG, SIGKILL);
        if (getppid() == 1) _exit(0);
        for (;;) {
            sleep(100);
        }
    }
    return (jint) pid;
}

/*
 * Reaps the given child without blocking.
 * Returns >0: the pid that was just reaped; 0: still running; -1: it is not our child.
 *
 * Per-pid polling, not waitpid(-1): a wider wait would also swallow children of the caller.
 */
JNIEXPORT jint JNICALL
Java_me_kanadex_phantomprobe_probe_PhantomProcessNative_waitPidNoHang(JNIEnv *env, jclass clazz,
                                                                  jint pid) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return -1;
    int status = 0;
    pid_t reaped = waitpid((pid_t) pid, &status, WNOHANG);
    if (reaped > 0) return (jint) reaped;
    if (reaped == 0) return 0;
    return (errno == ECHILD) ? -1 : 0;
}

JNIEXPORT void JNICALL
Java_me_kanadex_phantomprobe_probe_PhantomProcessNative_killPid(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    if (pid > 0) kill((pid_t) pid, SIGKILL);
}

/*
 * Waits up to timeoutMillis for the given child.
 * Returns the pid when reaped, 0 on timeout, -1 when the pid is not our child.
 */
JNIEXPORT jint JNICALL
Java_me_kanadex_phantomprobe_probe_PhantomProcessNative_reapPid(JNIEnv *env, jclass clazz, jint pid,
                                                            jint timeoutMillis) {
    (void) env;
    (void) clazz;
    if (pid <= 0) return -1;
    int status = 0;
    int waited = 0;
    for (;;) {
        pid_t reaped = waitpid((pid_t) pid, &status, WNOHANG);
        if (reaped == (pid_t) pid) return pid;
        if (reaped < 0) return -1;
        if (waited >= timeoutMillis) return 0;
        usleep(5 * 1000);
        waited += 5;
    }
}

} // extern "C"
