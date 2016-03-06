/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/g1/concurrentG1Refine.hpp"
#include "gc/g1/concurrentG1RefineThread.hpp"
#include "gc/g1/g1CollectedHeap.inline.hpp"
#include "gc/g1/g1HotCardCache.hpp"
#include "runtime/java.hpp"

ConcurrentG1Refine::ConcurrentG1Refine(G1CollectedHeap* g1h) :
  _threads(NULL),
  _sample_thread(NULL),
  _hot_card_cache(g1h)
{
  // Ergonomically select initial concurrent refinement parameters
  if (FLAG_IS_DEFAULT(G1ConcRefinementGreenZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementGreenZone, ParallelGCThreads);
  }
  set_green_zone(G1ConcRefinementGreenZone);

  if (FLAG_IS_DEFAULT(G1ConcRefinementYellowZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementYellowZone, green_zone() * 3);
  }
  set_yellow_zone(MAX2(G1ConcRefinementYellowZone, green_zone()));

  if (FLAG_IS_DEFAULT(G1ConcRefinementRedZone)) {
    FLAG_SET_DEFAULT(G1ConcRefinementRedZone, yellow_zone() * 2);
  }
  set_red_zone(MAX2(G1ConcRefinementRedZone, yellow_zone()));
}

ConcurrentG1Refine* ConcurrentG1Refine::create(G1CollectedHeap* g1h, CardTableEntryClosure* refine_closure, jint* ecode) {
  ConcurrentG1Refine* cg1r = new ConcurrentG1Refine(g1h);
  if (cg1r == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not create ConcurrentG1Refine");
    return NULL;
  }
  cg1r->_n_worker_threads = thread_num();

  cg1r->reset_threshold_step();

  cg1r->_threads = NEW_C_HEAP_ARRAY_RETURN_NULL(ConcurrentG1RefineThread*, cg1r->_n_worker_threads, mtGC);
  if (cg1r->_threads == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not allocate an array for ConcurrentG1RefineThread");
    return NULL;
  }

  uint worker_id_offset = DirtyCardQueueSet::num_par_ids();

  ConcurrentG1RefineThread *next = NULL;
  for (uint i = cg1r->_n_worker_threads - 1; i != UINT_MAX; i--) {
    ConcurrentG1RefineThread* t = new ConcurrentG1RefineThread(cg1r, next, refine_closure, worker_id_offset, i);
    assert(t != NULL, "Conc refine should have been created");
    if (t->osthread() == NULL) {
      *ecode = JNI_ENOMEM;
      vm_shutdown_during_initialization("Could not create ConcurrentG1RefineThread");
      return NULL;
    }

    assert(t->cg1r() == cg1r, "Conc refine thread should refer to this");
    cg1r->_threads[i] = t;
    next = t;
  }

  cg1r->_sample_thread = new G1YoungRemSetSamplingThread();
  if (cg1r->_sample_thread->osthread() == NULL) {
    *ecode = JNI_ENOMEM;
    vm_shutdown_during_initialization("Could not create G1YoungRemSetSamplingThread");
    return NULL;
  }

  *ecode = JNI_OK;
  return cg1r;
}

void ConcurrentG1Refine::reset_threshold_step() {
  if (FLAG_IS_DEFAULT(G1ConcRefinementThresholdStep)) {
    _thread_threshold_step = (yellow_zone() - green_zone()) / (worker_thread_num() + 1);
  } else {
    _thread_threshold_step = G1ConcRefinementThresholdStep;
  }
}

void ConcurrentG1Refine::init(G1RegionToSpaceMapper* card_counts_storage) {
  _hot_card_cache.initialize(card_counts_storage);
}

void ConcurrentG1Refine::stop() {
  for (uint i = 0; i < _n_worker_threads; i++) {
    _threads[i]->stop();
  }
  _sample_thread->stop();
}

void ConcurrentG1Refine::reinitialize_threads() {
  reset_threshold_step();
  for (uint i = 0; i < _n_worker_threads; i++) {
    _threads[i]->initialize();
  }
}

ConcurrentG1Refine::~ConcurrentG1Refine() {
  for (uint i = 0; i < _n_worker_threads; i++) {
    delete _threads[i];
  }
  FREE_C_HEAP_ARRAY(ConcurrentG1RefineThread*, _threads);

  delete _sample_thread;
}

void ConcurrentG1Refine::threads_do(ThreadClosure *tc) {
  worker_threads_do(tc);
  tc->do_thread(_sample_thread);
}

void ConcurrentG1Refine::worker_threads_do(ThreadClosure * tc) {
  for (uint i = 0; i < worker_thread_num(); i++) {
    tc->do_thread(_threads[i]);
  }
}

uint ConcurrentG1Refine::thread_num() {
  return G1ConcRefinementThreads;
}

void ConcurrentG1Refine::print_worker_threads_on(outputStream* st) const {
  for (uint i = 0; i < _n_worker_threads; ++i) {
    _threads[i]->print_on(st);
    st->cr();
  }
  _sample_thread->print_on(st);
  st->cr();
}
