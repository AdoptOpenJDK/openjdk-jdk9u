/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "runtime/arguments.hpp"
#include "runtime/os.hpp"
#include "runtime/thread.hpp"
#include "utilities/vmError.hpp"

#include <sys/types.h>
#include <sys/wait.h>
#include <thread.h>
#include <signal.h>

void VMError::show_message_box(char *buf, int buflen) {
  bool yes;
  do {
    error_string(buf, buflen);
    int len = (int)strlen(buf);
    char *p = &buf[len];

    jio_snprintf(p, buflen - len,
               "\n\n"
               "Do you want to debug the problem?\n\n"
               "To debug, run 'dbx - %d'; then switch to thread " INTX_FORMAT "\n"
               "Enter 'yes' to launch dbx automatically (PATH must include dbx)\n"
               "Otherwise, press RETURN to abort...",
               os::current_process_id(), os::current_thread_id());

    yes = os::message_box("Unexpected Error", buf);

    if (yes) {
      // yes, user asked VM to launch debugger
      jio_snprintf(buf, buflen, "dbx - %d", os::current_process_id());

      os::fork_and_exec(buf);
      yes = false;
    }
  } while (yes);
}

// handle all synchronous program error signals which may happen during error
// reporting. They must be unblocked, caught, handled.

static const int SIGNALS[] = { SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP }; // add more if needed
static const int NUM_SIGNALS = sizeof(SIGNALS) / sizeof(int);

// Space for our "saved" signal flags and handlers
static int resettedSigflags[NUM_SIGNALS];
static address resettedSighandler[NUM_SIGNALS];

static void save_signal(int idx, int sig)
{
  struct sigaction sa;
  sigaction(sig, NULL, &sa);
  resettedSigflags[idx]   = sa.sa_flags;
  resettedSighandler[idx] = (sa.sa_flags & SA_SIGINFO)
                              ? CAST_FROM_FN_PTR(address, sa.sa_sigaction)
                              : CAST_FROM_FN_PTR(address, sa.sa_handler);
}

int VMError::get_resetted_sigflags(int sig) {
  for (int i = 0; i < NUM_SIGNALS; i++) {
    if (SIGNALS[i] == sig) {
      return resettedSigflags[i];
    }
  }
  return -1;
}

address VMError::get_resetted_sighandler(int sig) {
  for (int i = 0; i < NUM_SIGNALS; i++) {
    if (SIGNALS[i] == sig) {
      return resettedSighandler[i];
    }
  }
  return NULL;
}

static void crash_handler(int sig, siginfo_t* info, void* ucVoid) {
  // unmask current signal
  sigset_t newset;
  sigemptyset(&newset);
  sigaddset(&newset, sig);
  // also unmask other synchronous signals
  for (int i = 0; i < NUM_SIGNALS; i++) {
    sigaddset(&newset, SIGNALS[i]);
  }
  thr_sigsetmask(SIG_UNBLOCK, &newset, NULL);

  VMError err(NULL, sig, NULL, info, ucVoid);
  err.report_and_die();
}

void VMError::reset_signal_handlers() {
  // install signal handlers for all synchronous program error signals
  sigset_t newset;
  sigemptyset(&newset);

  for (int i = 0; i < NUM_SIGNALS; i++) {
    save_signal(i, SIGNALS[i]);
    os::signal(SIGNALS[i], CAST_FROM_FN_PTR(void *, crash_handler));
    sigaddset(&newset, SIGNALS[i]);
  }
  thr_sigsetmask(SIG_UNBLOCK, &newset, NULL);
}
